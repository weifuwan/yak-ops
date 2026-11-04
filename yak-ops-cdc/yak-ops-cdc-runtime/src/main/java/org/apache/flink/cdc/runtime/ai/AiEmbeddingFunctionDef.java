package org.apache.flink.cdc.runtime.ai;

import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypes;

/** Built-in AI embedding function definitions with configurable input and output types. */
public enum AiEmbeddingFunctionDef {
    AI_EMBED("AI_EMBED", DataTypes.STRING(), DataTypes.ARRAY(DataTypes.FLOAT()));

    private final String functionName;
    private final DataType inputType;
    private final DataType outputType;

    AiEmbeddingFunctionDef(String functionName, DataType inputType, DataType outputType) {
        this.functionName = functionName;
        this.inputType = inputType;
        this.outputType = outputType;
    }

    public String getFunctionName() {
        return functionName;
    }

    /** The type of the input value (e.g. STRING for text embedding). */
    public DataType getInputType() {
        return inputType;
    }

    /** The type of the output value (e.g. ARRAY&lt;FLOAT&gt; for vector embedding). */
    public DataType getOutputType() {
        return outputType;
    }
}
