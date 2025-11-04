package com.github.paicoding.forum.api.model.vo.notify;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * @author YiHui
 * @date 2022/9/3
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
/* 自定以的一个监听实体类， 用于内部的监控
   使用到这个自定义监听实体类的有
    1. 监听端口：src/main/java/com/github/paicoding/forum/service/statistics/listener/UserStatisticEventListener.java
    2. 广播位置：src/main/java/com/github/paicoding/forum/service/user/service/userfoot/UserFootServiceImpl.java
 */
public class NotifyMsgEvent<T> extends ApplicationEvent {
    // 事件的类型
    private NotifyTypeEnum notifyType;
    // 事件的内容
    private T content;

    public NotifyMsgEvent(Object source, NotifyTypeEnum notifyType, T content) {
        super(source);
        this.notifyType = notifyType;
        this.content = content;
    }


}
