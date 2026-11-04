package io.yak.ops.plugin.task.api.parameters.resource;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "resourceType")
@JsonSubTypes({
        @Type(value = DataSourceParameters.class, name = "DATASOURCE")
})
public abstract class AbstractResourceParameters {

}
