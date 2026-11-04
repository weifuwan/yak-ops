package io.yak.ops.dao.model;

import io.yak.ops.dao.entity.DependentResultTaskInstanceContext;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "contextType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DependentResultTaskInstanceContext.class, name = "DEPENDENT_RESULT_CONTEXT")
})
public interface ITaskInstanceContext {

}
