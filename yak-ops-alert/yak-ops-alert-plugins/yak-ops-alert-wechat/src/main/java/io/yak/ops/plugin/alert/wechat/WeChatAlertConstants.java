package io.yak.ops.plugin.alert.wechat;

public final class WeChatAlertConstants {

    static final String MARKDOWN_QUOTE = ">";

    static final String MARKDOWN_ENTER = "\n";

    static final String WE_CHAT_PUSH_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token={token}";

    static final String WE_CHAT_APP_CHAT_PUSH_URL = "https://qyapi.weixin.qq.com/cgi-bin/appchat/send?access_token" +
            "={token}";

    static final String WE_CHAT_TOKEN_URL =
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}";

    static final String WE_CHAT_CONTENT_KEY = "content";

    static final Integer WE_CHAT_MESSAGE_SAFE_PUBLICITY = 0;

    static final Integer WE_CHAT_ENABLE_ID_TRANS = 0;

    static final Integer WE_CHAT_DUPLICATE_CHECK_INTERVAL_ZERO = 0;

    private WeChatAlertConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
