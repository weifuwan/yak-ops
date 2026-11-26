package io.yak.ops.modeling.ddl.model;

/** UI-independent description of a field type and its editor parameters. */
public class DdlFieldTypeOption {

    private final String code;
    private final String label;
    private final String parameterType;
    private final Integer maxLength;
    private final Integer maxPrecision;
    private final Integer maxScale;

    public DdlFieldTypeOption(
            String code, String label, String parameterType,
            Integer maxLength, Integer maxPrecision, Integer maxScale) {
        this.code = code;
        this.label = label;
        this.parameterType = parameterType;
        this.maxLength = maxLength;
        this.maxPrecision = maxPrecision;
        this.maxScale = maxScale;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getParameterType() { return parameterType; }
    public Integer getMaxLength() { return maxLength; }
    public Integer getMaxPrecision() { return maxPrecision; }
    public Integer getMaxScale() { return maxScale; }
}
