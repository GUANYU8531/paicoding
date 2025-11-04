package com.github.paicoding.forum.core.dal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author YiHui
 * @date 2023/4/30
 * 定义了一个注解, 标识方法或类使用的数据源
 */

@Retention(RetentionPolicy.RUNTIME) //这是一个元注解（用于修饰注解的注解），指定 DsAno 注解的生命周期。RetentionPolicy.RUNTIME表示该注解在 运行时仍然保留，可以通过反射机制在程序运行时获取注解信息
@Target({ElementType.METHOD, ElementType.TYPE}) // 指定这个注解能够修饰的目标, 这里允许修饰方法, 类/接口/枚举
public @interface DsAno {
    /**
     * 启用的数据源，默认主库
     * 为这个注解定义一个名为value 类型为MasterSlaveDsEnum 的一个属性, 初始默认值为Master主库
     * @return
     */
    MasterSlaveDsEnum value() default MasterSlaveDsEnum.MASTER;

    /**
     * 启用的数据源，如果存在，则优先使用它来替换默认的value
     * 定义一个名为ds, 类型为string的另一属性, 默认值为空
     * @return
     */
    String ds() default "";
}
