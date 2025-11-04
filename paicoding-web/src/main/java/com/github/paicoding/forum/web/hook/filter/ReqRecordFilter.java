package com.github.paicoding.forum.web.hook.filter;

import cn.hutool.core.date.StopWatch;
import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.core.async.AsyncUtil;
import com.github.paicoding.forum.core.mdc.MdcUtil;
import com.github.paicoding.forum.core.util.CrossUtil;
import com.github.paicoding.forum.core.util.EnvUtil;
import com.github.paicoding.forum.core.util.IpUtil;
import com.github.paicoding.forum.core.util.SessionUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.sitemap.service.impl.SitemapServiceImpl;
import com.github.paicoding.forum.service.statistics.service.StatisticsSettingService;
import com.github.paicoding.forum.service.user.service.LoginService;
import com.github.paicoding.forum.web.global.GlobalInitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 1. 请求参数日志输出过滤器
 * 2. 判断用户是否登录
 *
 * @author YiHui
 * @date 2022/7/6
 *
 * 一个http请求过来之后
 *     1. 首先进入filter，执行相关业务逻辑
 *     2. 如果判定通行，则进入Servlet逻辑， Servlet执行完毕之后又返回filter，最后再返回给请求方
 *     3. 如果判定失败，则直接返回，不需要将请求发送到Servlet
 */
@Slf4j
@WebFilter(urlPatterns = "/*", filterName = "reqRecordFilter", asyncSupported = true) // urlPatterns表示对所有请求进行过滤
public class ReqRecordFilter implements Filter { // Filter过滤器主要用于拦截http请求
    private static Logger REQ_LOG = LoggerFactory.getLogger("req");
    /**
     * 返回给前端的traceId，用于日志追踪
     */
    private static final String GLOBAL_TRACE_ID_HEADER = "g-trace-id";

    @Autowired
    private GlobalInitService globalInitService;

    @Autowired
    private StatisticsSettingService statisticsSettingService;

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    // ServletRequest : 客户端请求  ServletResponse: 服务端相应, FilterChain: 过滤器链
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        HttpServletRequest request = null;
        StopWatch stopWatch = new StopWatch("请求耗时");
        try {
            stopWatch.start("请求参数构建");
            request = this.initReqInfo((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
            stopWatch.stop();
            stopWatch.start("cors");
            CrossUtil.buildCors(request, (HttpServletResponse) servletResponse);
            stopWatch.stop();
            stopWatch.start("业务执行");
            filterChain.doFilter(request, servletResponse);
        } finally {
            if (stopWatch.isRunning()) {
                // 避免doFitler执行异常，导致上面的 stopWatch无法结束，这里先首当结束一下上次的计数
                stopWatch.stop();
            }
            stopWatch.start("输出请求日志");
            buildRequestLog(ReqInfoContext.getReqInfo(), request, System.currentTimeMillis() - start);
            // 一个链路请求完毕，清空MDC相关的变量(如GlobalTraceId，用户信息)
            MdcUtil.clear();
            ReqInfoContext.clear();
            stopWatch.stop();

            if (!isStaticURI(request) && !EnvUtil.isPro()) {
                log.info("{} - cost:\n{}", request.getRequestURI(), stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
            }
        }
    }

    @Override
    public void destroy() {
    }

    // 初始化请求参数， 返回一个HttpServletRequest类型的对象
    // 发送过来的请求对应的参数
    private HttpServletRequest initReqInfo(HttpServletRequest request, HttpServletResponse response) {
        if (isStaticURI(request)) {
            // 静态资源直接放行
            return request;
        }

        StopWatch stopWatch = new StopWatch("请求参数构建");
        try {
            stopWatch.start("traceId");
            // 添加全链路的traceId
            MdcUtil.addTraceId();
            stopWatch.stop();

            stopWatch.start("请求基本信息");
            // 手动写入一个session，借助 OnlineUserCountListener 实现在线人数实时统计
            /**
             * request.getSession()的作用是:
             *      1. 如果当前请求已经关联了一个 Session（通常通过浏览器 Cookie 中的 JSESSIONID 标识），则直接返回该 Session 对象；
             *      2. 如果当前请求没有关联 Session，则自动创建一个新的 Session，并返回该对象。
             * 在这里新建了一个Session并且添加了一个属性和对应值:
             *      key: "latestVisit"
             *      val: 访问时间毫秒数
             *
             */
            request.getSession().setAttribute("latestVisit", System.currentTimeMillis());

            // 这里创建了一个存储请求信息的对象实例
            ReqInfoContext.ReqInfo reqInfo = new ReqInfoContext.ReqInfo();
            // 从request中获取与主机信息相关的两个字段:
            // request.getHeader("X-Forwarded-Host")：获取代理转发的原始主机
            /*
                客户端请求 https://blog.example.com → 经过代理服务器 proxy.example.com 转发 → 代理向后端服务器发送请求时，
                会添加 X-Forwarded-Host: blog.example.com，而此时后端服务器收到的 host 头是 proxy.example.com。
             */
            String forwardedHost = request.getHeader("X-Forwarded-Host");
            // request.getHeader("host")：获取标准请求的目标主机, 当客户端直接访问 https://blog.example.com:8080/article 时，请求头中的 host 为 blog.example.com:8080；
            String hostHeader = request.getHeader("host");
            if (StringUtils.isNotBlank(forwardedHost)) { // StringUtils.isNotBlank 是一个严格的字符串有效性校验方法，通过排除 null、空串、纯空白字符串，确保判断的字符串包含实际有效的字符，
                // 需要配合修改nginx的转发，添加  proxy_set_header X-Forwarded-Host $host;
                reqInfo.setHost(forwardedHost);
            } else if (StringUtils.isNotBlank(hostHeader)) {
                reqInfo.setHost(hostHeader);
            } else {
                // 如果request中没有直接设置host和x-forwarded-host这两个session属性, 则从请求的url网址中直接截取
                URL reqUrl = new URL(request.getRequestURL().toString());
                reqInfo.setHost(reqUrl.getHost());
            }

            // 尝试从request中获取路径信息部分, 如果设置的话
            reqInfo.setPath(request.getPathInfo());
            if (reqInfo.getPath() == null) { // 如果没有设置, 则直接从url中截取路径信息
                String url = request.getRequestURI();
                int index = url.indexOf("?");
                if (index > 0) {
                    url = url.substring(0, index);
                }
                reqInfo.setPath(url);
            }
            // 进一步将: 1 页面来源url, 2 客户端的真实ip地址, 3 客户端的浏览器/设备标识信息, 4 客户端设备的唯一标识 信息存入
            reqInfo.setReferer(request.getHeader("referer"));
            reqInfo.setClientIp(IpUtil.getClientIp(request));
            reqInfo.setUserAgent(request.getHeader("User-Agent"));
            reqInfo.setDeviceId(getOrInitDeviceId(request, response));

            // 这里通过post包装类, 将请求体字符串存储到了request的session中
            request = this.wrapperRequest(request, reqInfo);
            stopWatch.stop();

            stopWatch.start("登录用户信息");
            // 初始化登录信息
            globalInitService.initLoginUser(reqInfo);
            stopWatch.stop();

            ReqInfoContext.addReqInfo(reqInfo);
            stopWatch.start("pv/uv站点统计");
            // 更新uv/pv计数
            // 1. 异步工具类将任务提交到异步线程池执行，避免该操作阻塞当前请求的处理流程
            // 2. 从 Spring 容器中获取负责处理统计逻辑的服务类实例
            // 3. 传入客户端ip和访问路径, 这里不是用用户标识而使用客户端标识是因为对于一些可以匿名访问的文章方便记录
            // 这里使用异步线程的原因是防止阻塞, 主方法中的业务流程, 这里是请求信息初始化流程
            AsyncUtil.execute(() -> SpringUtil.getBean(SitemapServiceImpl.class).saveVisitInfo(reqInfo.getClientIp(), reqInfo.getPath()));
            stopWatch.stop();

            stopWatch.start("回写traceId");
            // 返回头中记录traceId
            response.setHeader(GLOBAL_TRACE_ID_HEADER, Optional.ofNullable(MdcUtil.getTraceId()).orElse(""));
            stopWatch.stop();
        } catch (Exception e) {
            log.error("init reqInfo error!", e);
        } finally {
            if (!EnvUtil.isPro()) {
                log.info("{} -> 请求构建耗时: \n{}", request.getRequestURI(), stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
            }
        }

        return request;
    }

    private void buildRequestLog(ReqInfoContext.ReqInfo req, HttpServletRequest request, long costTime) {
        if (req == null || isStaticURI(request)) {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("method=").append(request.getMethod()).append("; ");
        if (StringUtils.isNotBlank(req.getReferer())) {
            msg.append("referer=").append(URLDecoder.decode(req.getReferer())).append("; ");
        }
        msg.append("remoteIp=").append(req.getClientIp());
        msg.append("; agent=").append(req.getUserAgent());

        if (req.getUserId() != null) {
            // 打印用户信息
            msg.append("; user=").append(req.getUserId());
        }

        msg.append("; uri=").append(request.getRequestURI());
        if (StringUtils.isNotBlank(request.getQueryString())) {
            msg.append('?').append(URLDecoder.decode(request.getQueryString()));
        }

        msg.append("; payload=").append(req.getPayload());
        msg.append("; cost=").append(costTime);
        REQ_LOG.info("{}", msg);

        // 保存请求计数
        statisticsSettingService.saveRequestCount(req.getClientIp());
    }


    private HttpServletRequest wrapperRequest(HttpServletRequest request, ReqInfoContext.ReqInfo reqInfo) {
        // 非POST请求直接返回原始request（因为GET等请求通常没有请求体，或请求体无意义）
        if (!HttpMethod.POST.name().equalsIgnoreCase(request.getMethod())) {
            return request;
        }
        // 创建自定义的请求包装器，包装原始request
        // post 流数据包装，避免因为打印日志导致请求参数被提前消费
        BodyReaderHttpServletRequestWrapper requestWrapper = new BodyReaderHttpServletRequestWrapper(request);
        // 从包装器中获取请求体字符串，存入reqInfo（记录请求内容）
        reqInfo.setPayload(requestWrapper.getBodyString());
        // 返回包装后的request，供后续处理（如控制器）使用
        return requestWrapper;
    }

    private boolean isStaticURI(HttpServletRequest request) {
        return request == null
                || request.getRequestURI().endsWith("css")
                || request.getRequestURI().endsWith("js")
                || request.getRequestURI().endsWith("png")
                || request.getRequestURI().endsWith("ico")
                || request.getRequestURI().endsWith("gif")
                || request.getRequestURI().endsWith("svg")
                || request.getRequestURI().endsWith("min.js.map")
                || request.getRequestURI().endsWith("min.css.map");
    }


    /**
     * 初始化设备id
     *
     * @return
     */
    private String getOrInitDeviceId(HttpServletRequest request, HttpServletResponse response) {
        String deviceId = request.getParameter("deviceId");
        if (StringUtils.isNotBlank(deviceId) && !"null".equalsIgnoreCase(deviceId)) {
            return deviceId;
        }

        Cookie device = SessionUtil.findCookieByName(request, LoginService.USER_DEVICE_KEY);
        if (device == null) {
            deviceId = UUID.randomUUID().toString();
            if (response != null) {
                response.addCookie(SessionUtil.newCookie(LoginService.USER_DEVICE_KEY, deviceId));
            }
            return deviceId;
        }
        return device.getValue();
    }
}
