// 声明当前类所在的包，属于论坛项目核心模块中的MDC上下文管理相关
package com.github.paicoding.forum.core.mdc;

// 导入Lombok的@Slf4j注解，用于自动生成日志对象log
import lombok.extern.slf4j.Slf4j;
// 导入Apache Commons工具类，用于字符串空值判断
import org.apache.commons.lang3.StringUtils;
// 导入AspectJ的ProceedingJoinPoint，用于环绕通知中获取目标方法信息并执行目标方法
import org.aspectj.lang.ProceedingJoinPoint;
// 导入AspectJ的环绕通知注解
import org.aspectj.lang.annotation.Around;
// 导入AspectJ的切面注解，标识当前类为AOP切面
import org.aspectj.lang.annotation.Aspect;
// 导入AspectJ的切入点注解，用于定义拦截规则
import org.aspectj.lang.annotation.Pointcut;
// 导入AspectJ的MethodSignature，用于获取目标方法的签名信息（如方法对象、参数等）
import org.aspectj.lang.reflect.MethodSignature;
// 导入Spring的BeansException，用于处理Spring Bean相关异常
import org.springframework.beans.BeansException;
// 导入Spring的ApplicationContext，用于获取Spring容器中的Bean
import org.springframework.context.ApplicationContext;
// 导入Spring的ApplicationContextAware接口，用于让当前类获取Spring应用上下文
import org.springframework.context.ApplicationContextAware;
// 导入Spring的BeanFactoryResolver，用于在SPEL表达式中解析Spring Bean
import org.springframework.context.expression.BeanFactoryResolver;
// 导入Spring的DefaultParameterNameDiscoverer，用于获取方法参数的名称
import org.springframework.core.DefaultParameterNameDiscoverer;
// 导入Spring的ParameterNameDiscoverer接口，定义获取方法参数名的规范
import org.springframework.core.ParameterNameDiscoverer;
// 导入Spring的ExpressionParser接口，用于解析表达式
import org.springframework.expression.ExpressionParser;
// 导入Spring的SpelExpressionParser，SPEL表达式的具体解析器实现
import org.springframework.expression.spel.standard.SpelExpressionParser;
// 导入Spring的StandardEvaluationContext，SPEL表达式的评估上下文（用于存储变量、Bean解析器等）
import org.springframework.expression.spel.support.StandardEvaluationContext;
// 导入Spring的Component注解，将当前类注册为Spring容器管理的Bean
import org.springframework.stereotype.Component;

// 导入Java反射的Method类，用于表示方法对象
import java.lang.reflect.Method;

/**
 * @author YiHui
 * @date 2023/5/26
 * 该类是MDC（映射诊断上下文）的AOP切面类，用于自动为被@MdcDot注解标注的方法/类添加日志上下文信息
 */
@Slf4j // Lombok注解：自动生成log日志对象，用于输出日志
@Aspect // 标识当前类为AOP切面类，用于定义切入点和通知（增强逻辑）
@Component // 将当前类注册到Spring容器中，使其能被自动扫描并生效
public class MdcAspect implements ApplicationContextAware { // 实现ApplicationContextAware接口，以便获取Spring应用上下文
    // 初始化SPEL表达式解析器，用于解析@MdcDot注解中定义的表达式
    private ExpressionParser parser = new SpelExpressionParser();
    // 初始化参数名发现器，用于获取目标方法的参数名称（支持Java 8的参数名保留特性）
    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 定义切入点：拦截所有被@MdcDot注解标注的方法，或所在类被@MdcDot标注的方法
     * @annotation(MdcDot)：匹配方法上直接标注@MdcDot的方法
     * @within(MdcDot)：匹配类上标注@MdcDot的类中所有方法
     */
    @Pointcut("@annotation(MdcDot) || @within(MdcDot)")
    public void getLogAnnotation() {
        // 切入点方法无需实现逻辑，仅用于定义拦截规则
    }

    /**
     * 环绕通知：对切入点匹配的方法执行前后添加增强逻辑
     * @param joinPoint 连接点对象，包含目标方法的信息（如方法名、参数、所在类等）
     * @return 目标方法的返回值
     * @throws Throwable 目标方法执行可能抛出的异常
     */
    @Around("getLogAnnotation()") // 关联切入点getLogAnnotation，对匹配的方法执行环绕增强
    public Object handle(ProceedingJoinPoint joinPoint) throws Throwable {
        // 记录目标方法执行的开始时间（用于后续计算耗时）
        long start = System.currentTimeMillis();
        // 调用addMdcCode方法，向MDC中添加业务标识（bizCode），返回是否成功添加
        boolean hasTag = addMdcCode(joinPoint);
        try {
            // 执行目标方法（被拦截的业务方法），并获取返回结果
            Object ans = joinPoint.proceed();
            // 返回目标方法的执行结果
            return ans;
        } finally {
            // 无论目标方法是否抛出异常，最终都记录方法执行耗时
            log.info("执行耗时: {}#{} = {}ms",
                    // 获取目标方法所在类的简单类名（如UserService）
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    // 获取目标方法的方法名
                    joinPoint.getSignature().getName(),
                    // 计算耗时：当前时间 - 开始时间
                    System.currentTimeMillis() - start);
            // 若之前成功添加了MDC标识，执行清理（避免线程复用导致的上下文污染）
            if (hasTag) {
                MdcUtil.reset();
            }
        }
    }

    /**
     * 向MDC中添加业务标识（bizCode）
     * @param joinPoint 连接点对象，包含目标方法信息
     * @return 是否成功添加MDC标识（true：添加成功；false：未添加）
     */
    private boolean addMdcCode(ProceedingJoinPoint joinPoint) {
        // 将连接点的签名转换为MethodSignature（获取方法相关信息）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 通过方法签名获取目标方法的Method对象
        Method method = signature.getMethod();
        // 从目标方法上获取@MdcDot注解（优先方法级注解）
        MdcDot dot = method.getAnnotation(MdcDot.class);
        // 若方法上没有@MdcDot注解，则从方法所在的类上获取@MdcDot注解（类级注解）
        if (dot == null) {
            dot = (MdcDot) joinPoint.getSignature().getDeclaringType().getAnnotation(MdcDot.class);
        }

        // 若注解存在（方法或类上有@MdcDot）
        if (dot != null) {
            // 解析注解的bizCode属性（可能是SPEL表达式），并将结果以"bizCode"为键添加到MDC
            MdcUtil.add("bizCode", loadBizCode(dot.bizCode(), joinPoint));
            // 返回true表示已添加MDC标识
            return true;
        }
        // 注解不存在，返回false表示未添加
        return false;
    }

    /**
     * 解析@MdcDot注解中bizCode属性的SPEL表达式，获取实际业务标识值
     * @param key bizCode属性值（可能是SPEL表达式，如"#userId"、"#{orderService.getOrderNo()}"）
     * @param joinPoint 连接点对象，用于获取目标方法的参数信息
     * @return 解析后的业务标识字符串
     */
    private String loadBizCode(String key, ProceedingJoinPoint joinPoint) {
        // 若表达式为空，直接返回空字符串
        if (StringUtils.isBlank(key)) {
            return "";
        }

        // 创建SPEL表达式的评估上下文（用于存储变量、Bean解析器等）
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 向上下文注册Spring Bean解析器，支持在表达式中引用Spring容器中的Bean（如"#{userService.getById(1)}"）
        context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        // 获取目标方法的参数名称（如方法参数为"Long userId"，则参数名为"userId"）
        String[] params = parameterNameDiscoverer.getParameterNames(((MethodSignature) joinPoint.getSignature()).getMethod());
        // 获取目标方法的实际参数值（如调用方法时传入的userId=123）
        Object[] args = joinPoint.getArgs();
        // 将方法参数名和参数值存入上下文（作为变量，支持表达式中通过"#参数名"引用，如"#userId"）
        for (int i = 0; i < args.length; i++) {
            context.setVariable(params[i], args[i]);
        }
        // 解析SPEL表达式，从上下文中获取值并转换为字符串（即最终的业务标识）
        return parser.parseExpression(key).getValue(context, String.class);
    }

    // 存储Spring应用上下文（通过ApplicationContextAware接口的set方法赋值）
    private ApplicationContext applicationContext;

    /**
     * 实现ApplicationContextAware接口的方法，Spring容器会自动调用该方法传入应用上下文
     * @param applicationContext Spring应用上下文对象
     * @throws BeansException 当获取上下文失败时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}