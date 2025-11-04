package com.github.paicoding.forum.core.mdc;

import org.slf4j.MDC;

/**
 * MDC（Mapped Diagnostic Context，映射诊断上下文）工具类
 * 用于管理日志上下文信息（如traceId），实现多线程环境下的日志链路追踪
 */
public class MdcUtil {
    /**
     * traceId在MDC中的存储键名
     * traceId是分布式系统中标识一次完整请求链路的唯一ID
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 向MDC中添加自定义的键值对上下文信息
     * @param key 上下文信息的键（如"userId"、"requestUrl"）
     * @param val 上下文信息的值（如具体的用户ID、请求地址）
     */
    public static void add(String key, String val) {
        // 调用SLF4J的MDC.put方法，将键值对存入当前线程的诊断上下文
        MDC.put(key, val);
    }

    /**
     * 生成并添加traceId到MDC中
     * 用于标识一次完整的请求链路，贯穿整个请求的处理过程
     */
    public static void addTraceId() {
        // 生成traceId（使用自定义的生成器SelfTraceIdGenerator），并存入MDC
        // 注：实际项目中可选择自定义生成策略或集成SkyWalking等工具的traceId
        MDC.put(TRACE_ID_KEY, SelfTraceIdGenerator.generate());
    }

    /**
     * 获取当前MDC中的traceId
     * @return 当前请求的traceId（若未设置则返回null）
     */
    public static String getTraceId() {
        // 从MDC中获取traceId的值
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 重置MDC上下文（保留traceId，清除其他信息）
     * 用于在请求处理的中间环节（如跨线程传递后）清理无关上下文，避免干扰后续日志
     */
    public static void reset() {
        // 先保存当前的traceId（避免被清除）
        String traceId = MDC.get(TRACE_ID_KEY);
        // 清空MDC中所有上下文信息
        MDC.clear();
        // 重新将traceId放入MDC（确保后续日志仍能关联到同一请求链路）
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 清空MDC中所有上下文信息
     * 通常在请求处理完成后调用，避免线程复用（如线程池）导致的上下文污染
     */
    public static void clear() {
        // 调用SLF4J的MDC.clear方法，清空当前线程的所有诊断上下文信息
        MDC.clear();
    }
}