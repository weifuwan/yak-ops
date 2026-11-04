package io.yak.ops.alert.api;

public final class AlertConstants {

    /**
     * the field name of alert show type
     **/
    public static final String SHOW_TYPE = "$t('showType')";

    public static final String NAME_SHOW_TYPE = "showType";

    public static final String WARNING_TYPE = "warningType";

    public static final String NAME_WARNING_TYPE = "WarningType";
    public static final String TEST_TITLE = "YakOps test alert";
    public static final String TEST_CONTENT = "[{\"message\":\" This is a test alert message form YakOps\"}]";

    private AlertConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
