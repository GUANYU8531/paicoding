package com.github.paicoding.forum.web.front.test.rest

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Specification

/**
 * 集成测试：验证真实发信能力
 */
@SpringBootTest
@AutoConfigureMockMvc
class TestControllerIntegrationTest extends Specification {

    @Autowired
    MockMvc mockMvc

    def "test real email send"() {
        when: "执行真实邮件发送"
        def result = mockMvc.perform(MockMvcRequestBuilders
                .get("/test/email")
                .param("to", "15116029307@163.com"))
                .andReturn()
                .getResponse()
                .getContentAsString()

        then: "返回结果应包含true"
        result.contains("true")
    }
}
