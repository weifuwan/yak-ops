package io.yak.ops.extract.master.command;

import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class AbstractCommandParam implements ICommandParam {

    protected boolean subWorkflowInstance;

    protected List<Long> startNodes;

    protected List<Property> commandParams;

    protected String timeZone;

}
