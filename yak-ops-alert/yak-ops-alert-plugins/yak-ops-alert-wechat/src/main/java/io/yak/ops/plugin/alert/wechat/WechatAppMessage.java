package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.ShowType;

import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WechatAppMessage {

    private String touser;
    private String msgtype;
    private Integer agentid;
    private Map<String, String> text;
    private Map<String, String> markdown;
    private Integer safe;
    private Integer enable_id_trans;
    private Integer enable_duplicate_check;

    public WechatAppMessage(String touser, String msgtype, Integer agentid, Map<String, String> contentMap,
                            Integer safe, Integer enableIdTrans, Integer enableDuplicateCheck) {
        this.touser = touser;
        this.msgtype = msgtype;
        this.agentid = agentid;
        if (msgtype.equals(ShowType.MARKDOWN.getDescp())) {
            this.markdown = contentMap;
        } else {
            this.text = contentMap;
        }
        this.safe = safe;
        this.enable_id_trans = enableIdTrans;
        this.enable_duplicate_check = enableDuplicateCheck;
    }
}
