package io.yak.ops.plugin.alert.feishu;

public final class FeiShuParamsConstants {

    public static final String FEI_SHU_PROXY_ENABLE = "$t('isEnableProxy')";
    static final String WEB_HOOK = "$t('webhook')";
    static final String NAME_WEB_HOOK = "WebHook";
    static final String NAME_FEI_SHU_PROXY_ENABLE = "IsEnableProxy";

    static final String FEI_SHU_PROXY = "$t('proxy')";

    static final String NAME_FEI_SHU_PROXY = "Proxy";

    static final String FEI_SHU_PORT = "$t('port')";

    static final String NAME_FEI_SHU_PORT = "Port";

    static final String FEI_SHU_USER = "$t('user')";

    static final String NAME_FEI_SHU_USER = "User";

    static final String FEI_SHU_PASSWORD = "$t('password')";

    static final String NAME_FEI_SHU_PASSWORD = "Password";

    private FeiShuParamsConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
