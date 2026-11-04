package io.yak.ops.plugin.task.api.parameters.resource;

import io.yak.ops.spi.enums.DbType;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataSourceParameters extends AbstractResourceParameters {

    private DbType type;

    @JsonProperty(value = "DATASOURCE")
    private String resourceType;

    private String connectionParams;

}
