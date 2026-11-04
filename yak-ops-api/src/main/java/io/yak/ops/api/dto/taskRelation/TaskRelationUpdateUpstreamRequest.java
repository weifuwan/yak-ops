package io.yak.ops.api.dto.taskRelation;

import static io.yak.ops.common.constants.Constants.COMMA;

import io.yak.ops.api.dto.PageQueryDto;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * task relation update request
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TaskRelationUpdateUpstreamRequest extends PageQueryDto {

    @Schema(example = "1234587654321", description = "workflow code ")
    private long workflowCode;

    @Schema(example = "12345678,87654321", required = true, description = "upstream you want to update separated by comma")
    private String upstreams;

    public List<Long> getUpstreams() {
        return Stream.of(this.upstreams.split(COMMA))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
