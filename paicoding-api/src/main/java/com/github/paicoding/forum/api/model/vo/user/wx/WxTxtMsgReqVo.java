package com.github.paicoding.forum.api.model.vo.user.wx;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

/**
 * 简单文本请求
 *
 * @author yihui
 * @link <a href="https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Receiving_standard_messages.html"/>
 * @date 2022/6/20
 * 注意: 这里的可以理解为当普通微信用户向公众账号发消息时，微信服务器将POST消息的XML数据包到开发者填写的URL上。
 *      这些就是传输过来的参数结构
 */
@Data
@JacksonXmlRootElement(localName = "xml")
public class WxTxtMsgReqVo {
    @JacksonXmlProperty(localName = "ToUserName") // 开发者微信号
    private String toUserName;
    @JacksonXmlProperty(localName = "FromUserName")//发送消息到公众号的用户OpenId
    private String fromUserName;
    @JacksonXmlProperty(localName = "CreateTime") // 消息创建时间
    private Long createTime;
    @JacksonXmlProperty(localName = "MsgType")  // 消息类型， 文本为text
    private String msgType;
    @JacksonXmlProperty(localName = "Event")  //
    private String event;
    @JacksonXmlProperty(localName = "EventKey")
    private String eventKey;
    @JacksonXmlProperty(localName = "Ticket")
    private String ticket;
    @JacksonXmlProperty(localName = "Content") // 文本消息内容
    private String content;
    @JacksonXmlProperty(localName = "MsgId")  // 消息id
    private String msgId;
    @JacksonXmlProperty(localName = "MsgDataId") // 消息的数据id
    private String msgDataId;
    @JacksonXmlProperty(localName = "Idx")
    private String idx;
}
