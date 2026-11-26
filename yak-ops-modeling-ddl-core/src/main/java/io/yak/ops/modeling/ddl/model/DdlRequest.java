package io.yak.ops.modeling.ddl.model;

/** Common request envelope; physical database choices live in the dialect options. */
public class DdlRequest {

    private final DdlTableDefinition table;
    private final DdlPublishMode publishMode;
    private final DdlOptions options;

    public DdlRequest(DdlTableDefinition table, DdlPublishMode publishMode, DdlOptions options) {
        this.table = table;
        this.publishMode = publishMode;
        this.options = options;
    }

    public DdlTableDefinition getTable() { return table; }
    public DdlPublishMode getPublishMode() { return publishMode; }
    public DdlOptions getOptions() { return options; }

    public <T extends DdlOptions> T getOptions(Class<T> optionType) {
        if (options == null || !optionType.isInstance(options)) {
            throw new IllegalArgumentException("DDL 请求缺少 " + optionType.getSimpleName() + " 配置");
        }
        return optionType.cast(options);
    }
}
