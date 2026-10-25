package io.yak.ops.common.bean.dto.task;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Non-secret current Data Sync parameters for a read-only DDL preview. */
@Data
public class SchemaPreviewDTO {
    /** Product JSON containing owned Source and Sink routes, never executable SQL. */
    @NotBlank(message = "请填写同步配置")
    @Size(max = 262144, message = "同步配置过大")
    private String parameters;
    /** Reject stringified JSON and arrays rather than silently accepting the old wire format. */
    @JsonSetter("parameters")
    public void readParameters(JsonNode parameters) {
        this.parameters = TaskParameterJson.requiredObject(parameters);
    }
}
