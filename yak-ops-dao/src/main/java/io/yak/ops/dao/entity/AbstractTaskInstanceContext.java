package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.ContextType;
import io.yak.ops.dao.model.ITaskInstanceContext;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class AbstractTaskInstanceContext implements ITaskInstanceContext {

    protected ContextType contextType;
}
