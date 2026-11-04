package io.yak.ops.plugin.alert.script;

public final class ScriptParamsConstants {

    static final String SCRIPT_TYPE = "$t('scriptType')";

    static final String NAME_SCRIPT_TYPE = "type";

    static final String SCRIPT_PATH = "$t('scriptPath')";

    static final String NAME_SCRIPT_PATH = "path";

    static final String SCRIPT_USER_PARAMS = "$t('userParams')";

    static final String NAME_SCRIPT_USER_PARAMS = "userParams";

    private ScriptParamsConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
