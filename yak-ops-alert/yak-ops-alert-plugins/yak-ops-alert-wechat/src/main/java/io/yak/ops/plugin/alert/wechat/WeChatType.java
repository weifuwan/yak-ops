package io.yak.ops.plugin.alert.wechat;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum WeChatType {

    APP(1, "APP/应用"),
    APPCHAT(2, "GROUP CHAT/群聊"),
    ;

    private final int code;
    private final String descp;
}
