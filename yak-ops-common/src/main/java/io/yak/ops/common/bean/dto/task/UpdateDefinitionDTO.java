package io.yak.ops.common.bean.dto.task;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Metadata and optional executable parameters for one Task Definition update. */
@Data
public class UpdateDefinitionDTO {
    /** Revision read by the editor; a stale revision never overwrites another edit. */
    @NotNull
    @Min(1)
    private Long expectedRevision;

    /** Updated display name of the definition. */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** Optional plugin-specific JSON; omitting it retains the current parameters. */
    private String parameters;

    /** Optional diagnostics toggle; omitting preserves the existing setting. */
    private Boolean debug;

    /** Optional human-readable description. */
    @Size(max = 500)
    private String remark;
    /** Reject stringified JSON and arrays rather than silently accepting the old wire format. */
    @JsonSetter("parameters")
    public void readParameters(JsonNode parameters) {
        this.parameters = TaskParameterJson.requiredObject(parameters);
    }
}
