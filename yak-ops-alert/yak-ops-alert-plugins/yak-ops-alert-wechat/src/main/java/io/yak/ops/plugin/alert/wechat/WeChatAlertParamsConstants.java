package io.yak.ops.plugin.alert.wechat;

public final class WeChatAlertParamsConstants {

    static final String ENTERPRISE_WE_CHAT_CORP_ID = "$t('corpId')";
    static final String NAME_ENTERPRISE_WE_CHAT_CORP_ID = "corpId";
    static final String ENTERPRISE_WE_CHAT_SECRET = "$t('secret')";
    static final String NAME_ENTERPRISE_WE_CHAT_SECRET = "secret";
    static final String ENTERPRISE_WE_CHAT_TEAM_SEND_MSG = "$t('teamSendMsg')";
    static final String NAME_ENTERPRISE_WE_CHAT_TEAM_SEND_MSG = "teamSendMsg";
    static final String ENTERPRISE_WE_CHAT_AGENT_ID = "$t('agentId/chatId')";
    static final String NAME_ENTERPRISE_WE_CHAT_AGENT_ID = "agentId/chatId";
    static final String ENTERPRISE_WE_CHAT_USERS = "$t('users')";
    static final String NAME_ENTERPRISE_WE_CHAT_USERS = "users";

    static final String NAME_ENTERPRISE_WE_CHAT_SEND_TYPE = "sendType";

    static final String ENTERPRISE_WE_CHAT_SEND_TYPE = "send.type";

    private WeChatAlertParamsConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
