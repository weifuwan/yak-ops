package io.yak.ops.plugin.alert.http;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class HttpAlertConstants {

    public static final String URL = "$t('url')";

    public static final String NAME_URL = "url";

    public static final String HEADER_PARAMS = "$t('headerParams')";

    public static final String NAME_HEADER_PARAMS = "headerParams";

    public static final String CONTENT_TYPE = "$t('contentType')";

    public static final String NAME_CONTENT_TYPE = "contentType";

    public static final String BODY_PARAMS = "$t('bodyParams')";

    public static final String NAME_BODY_PARAMS = "bodyParams";

    public static final String REQUEST_TYPE = "$t('requestType')";

    public static final String NAME_REQUEST_TYPE = "requestType";

    public static final String TIMEOUT = "$t('timeout')";

    public static final String NAME_TIMEOUT = "timeout";

    public static final int DEFAULT_TIMEOUT = 120;

    public static final String MSG_PARAMS = "${msg}";
}
