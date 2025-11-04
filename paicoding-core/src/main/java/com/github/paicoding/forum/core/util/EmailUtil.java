package com.github.paicoding.forum.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.internet.MimeMessage;

/**
 * @author YiHui
 * @date 2023/3/19
 */
@Slf4j
public class EmailUtil {
    private static volatile String from;

    // 这里使用了double lock机制
    public static String getFrom() {
        if (from == null) {
            synchronized (EmailUtil.class) {
                if (from == null) {
                    // 从 Spring 的配置环境（application.yml）中读取指定 key 的配置值。相当于一个全局的配置读取工具方法。
//                    from = SpringUtil.getConfig("spring.mail.from", "xhhuiblog@163.com");//
                    from = SpringUtil.getConfig("spring.mail.from", "15116029307@163.com");// 如果配置中的spring.mail.from：为null， 则使用默认邮箱15116029307@163.com
                }
            }
        }
        return from;
    }

    /**
     * springboot-email封装的发送邮件
     *
     * @param title
     * @param to
     * @param content
     * @return
     */
    public static boolean sendMail(String title, String to, String content) {
        try {
            // 从spring容器中获取到对象
            JavaMailSender javaMailSender = SpringUtil.getBean(JavaMailSender.class);
            MimeMessage mimeMailMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMailMessage, true);
            mimeMessageHelper.setFrom(getFrom()); // 传入邮箱
            mimeMessageHelper.setTo(to); // 收件邮箱
            mimeMessageHelper.setSubject(title); // 邮件主题
            //邮件内容，第二个参数设置为true，支持html模板
            mimeMessageHelper.setText(content, true);
            // 解决 JavaMailSender no object DCH for MIME type multipart/mixed 问题
            // 详情参考：[Email发送失败问题记录 - 一灰灰Blog](https://blog.hhui.top/hexblog/2021/10/28/211028-Email%E5%8F%91%E9%80%81%E5%A4%B1%E8%B4%A5%E9%97%AE%E9%A2%98%E8%AE%B0%E5%BD%95/)
            Thread.currentThread().setContextClassLoader(EmailUtil.class.getClassLoader());
            javaMailSender.send(mimeMailMessage);
            return true;
        } catch (Exception e) {
            log.warn("sendEmail error {}@{}", title, to, e);
            return false;
        }
    }

}
