package io.yak.ops.dao.entity;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TaskInstanceDependentDetails<T extends AbstractTaskInstanceContext> extends TaskInstance {

    private List<T> taskInstanceDependentResults;

}
