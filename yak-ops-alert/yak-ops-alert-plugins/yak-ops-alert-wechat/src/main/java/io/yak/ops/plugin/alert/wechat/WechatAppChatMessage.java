package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.ShowType;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WechatAppChatMessage {

    private String chatid;
    private String msgtype;
    private Map<String, String> text;
    private Map<String, String> markdown;
    private Integer safe;

    public WechatAppChatMessage(String chatid, String msgtype, Map<String, String> contentMap, Integer safe) {
        this.chatid = chatid;
        this.msgtype = msgtype;
        if (msgtype.equals(ShowType.MARKDOWN.getDescp())) {
            this.markdown = contentMap;
        } else {
            this.text = contentMap;
        }
        this.safe = safe;
    }
}
