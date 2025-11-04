package com.github.paicoding.forum.core.dal;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * @author YiHui
 * @date 2023/4/30
 */
@Aspect
public class DsAspect {
    /**
     * 切入点, 拦截类上、方法上有注解的方法，用于切换数据源
     * 基于 Spring AOP 实现动态数据源切换的核心代码，用于拦截带有 @DsAno 注解的类或方法，从而在运行时切换对应的数据源。
     */
    @Pointcut("@annotation(com.github.paicoding.forum.core.dal.DsAno) || @within(com.github.paicoding.forum.core.dal.DsAno)")
    public void pointcut() {
    }

    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        DsAno ds = getDsAno(proceedingJoinPoint);
        try {
            if (ds != null && (StringUtils.isNotBlank(ds.ds()) || ds.value() != null)) {
                // 当上下文中没有时，则写入线程上下文，应该用哪个DB
                DsContextHolder.set(StringUtils.isNoneBlank(ds.ds()) ? ds.ds() : ds.value().name());
            }
            return proceedingJoinPoint.proceed();
        } finally {
            // 清空上下文信息
            if (ds != null) {
                DsContextHolder.reset();
            }
        }
    }

    /**
     *
     * @param proceedingJoinPoint
     * @return
     *  从被拦截的方法或类上获取 @DsAno 注解实例
     */
    private DsAno getDsAno(ProceedingJoinPoint proceedingJoinPoint) {
        MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
        Method method = signature.getMethod();
        // 先从方法上获取 @DsAno 注解（method.getAnnotation(DsAno.class)）
        DsAno ds = method.getAnnotation(DsAno.class);
        if (ds == null) {
            // 获取类上的注解
            ds = (DsAno) proceedingJoinPoint.getSignature().getDeclaringType().getAnnotation(DsAno.class);
        }
        return ds;
    }
}
