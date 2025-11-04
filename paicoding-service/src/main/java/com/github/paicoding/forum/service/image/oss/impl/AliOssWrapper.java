package com.github.paicoding.forum.service.image.oss.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.github.paicoding.forum.core.autoconf.DynamicConfigContainer;
import com.github.paicoding.forum.core.config.ImageProperties;
import com.github.paicoding.forum.core.util.Md5Util;
import com.github.paicoding.forum.core.util.StopWatchUtil;
import com.github.paicoding.forum.service.image.oss.ImageUploader;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 阿里云oss文件上传
 *
 * @author YiHui
 * @date 2023/1/12
 */
@Slf4j
@ConditionalOnExpression(value = "#{'ali'.equals(environment.getProperty('image.oss.type'))}")
@Component
public class AliOssWrapper implements ImageUploader, InitializingBean, DisposableBean {
    private static final int SUCCESS_CODE = 200;
    @Autowired
    @Setter
    @Getter
    private ImageProperties properties;
    private OSS ossClient;

    @Autowired
    private DynamicConfigContainer dynamicConfigContainer;

    public String upload(InputStream input, String fileType) {
        try {
            // 创建PutObjectRequest对象。
            byte[] bytes = StreamUtils.copyToByteArray(input);
            return upload(bytes, fileType);
        } catch (OSSException oe) {
            log.error("Oss rejected with an error response! msg:{}, code:{}, reqId:{}, host:{}", oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            return "";
        } catch (Exception ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network. {}", ce.getMessage());
            return "";
        }
    }

    /**
     *
     * @param bytes : 要传输到oss的文件(图片, 文件)
     * @param fileType: 文件的类型
     * @return
     */
    public String upload(byte[] bytes, String fileType) {
        StopWatchUtil stopWatchUtil = StopWatchUtil.init("图片上传");
        try {
            // 计算md5作为文件名，避免重复上传, md5是一种基于文件内容的哈希算法:相同内容的文件会计算出相同的MD5值, 不同的文件,即使文件名相同, 也会得到不同的md5值
            String fileName = stopWatchUtil.record("md5计算", () -> Md5Util.encode(bytes));
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            fileName = properties.getOss().getPrefix() + fileName + "." + getFileType(input, fileType);
            // 创建PutObjectRequest对象。PutObjectRequest 是对象存储服务（如阿里云 OSS、AWS S3 等）的 SDK 中提供的一个核心类，用于构建 “上传对象（文件）到存储服务” 的请求参数。
            // 它的作用是将上传所需的各种信息（如目标存储空间、文件名、文件内容等）封装成一个统一的请求对象，再传递给存储服务客户端（如 OSS 客户端）执行上传操作。
            PutObjectRequest putObjectRequest = new PutObjectRequest(properties.getOss().getBucket(), fileName, input);
            // 设置该属性可以返回response。如果不设置，则返回的response为空。
            putObjectRequest.setProcess("true");

            // 上传文件, 这里用到了stopWatchUtil计时工具类, 第二个参数使用lamda表达式通过oss客户端发送包装的请求到oss
            PutObjectResult result = stopWatchUtil.record("文件上传", () -> ossClient.putObject(putObjectRequest));
            if (SUCCESS_CODE == result.getResponse().getStatusCode()) {
                return properties.getOss().getHost() + fileName;
            } else {
                log.error("upload to oss error! response:{}", result.getResponse().getStatusCode());
                // Guava 不允许回传 null
                return "";
            }
        } catch (OSSException oe) {
            log.error("Oss rejected with an error response! msg:{}, code:{}, reqId:{}, host:{}", oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            return  "";
        } catch (Exception ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network. {}", ce.getMessage());
            return  "";
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("upload image size:{} cost: {}", bytes.length, stopWatchUtil.prettyPrint());
            }
        }
    }

    public String uploadWithFileName(byte[] bytes, String fileName) {
        StopWatchUtil stopWatchUtil = StopWatchUtil.init("图片上传");
        try {
            // 计算md5作为文件名，避免重复上传
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            fileName = properties.getOss().getPrefix() + fileName;
            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(properties.getOss().getBucket(), fileName, input);
            // 设置该属性可以返回response。如果不设置，则返回的response为空。
            putObjectRequest.setProcess("true");

            // 上传文件
            PutObjectResult result = stopWatchUtil.record("文件上传", () -> ossClient.putObject(putObjectRequest));
            if (SUCCESS_CODE == result.getResponse().getStatusCode()) {
                return properties.getOss().getHost() + fileName;
            } else {
                log.error("upload to oss error! response:{}", result.getResponse().getStatusCode());
                // Guava 不允许回传 null
                return "";
            }
        } catch (OSSException oe) {
            log.error("Oss rejected with an error response! msg:{}, code:{}, reqId:{}, host:{}", oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            return  "";
        } catch (Exception ce) {
            log.error("Caught an ClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with OSS, "
                    + "such as not being able to access the network. {}", ce.getMessage());
            return  "";
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("upload image size:{} cost: {}", bytes.length, stopWatchUtil.prettyPrint());
            }
        }
    }

    @Override
    public boolean uploadIgnore(String fileUrl) {
        // 这里用if语句判断URL是不是CDN开头(是否配置了加速)
        if (StringUtils.isNotBlank(properties.getOss().getHost()) && fileUrl.startsWith(properties.getOss().getHost())) {
            return true;
        }

        return !fileUrl.startsWith("http");
    }

    @Override
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    private void init() {
        // 创建OSSClient实例。
        log.info("init ossClient");
        ossClient = new OSSClientBuilder().build(properties.getOss().getEndpoint(), properties.getOss().getAk(), properties.getOss().getSk());
    }

    @Override
    public void afterPropertiesSet() {
        init();
//        // 监听配置变更，然后重新初始化OSSClient实例
//        dynamicConfigContainer.registerRefreshCallback(properties, () -> {
//            init();
//            log.info("ossClient refreshed!");
//        });
    }
}
