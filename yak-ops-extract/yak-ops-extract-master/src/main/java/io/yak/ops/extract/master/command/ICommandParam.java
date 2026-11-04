package io.yak.ops.extract.master.command;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true, property = "commandType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ScheduleWorkflowCommandParam.class, name = "SCHEDULER"),
        @JsonSubTypes.Type(value = RunWorkflowCommandParam.class, name = "START_PROCESS"),
        @JsonSubTypes.Type(value = BackfillWorkflowCommandParam.class, name = "COMPLEMENT_DATA"),
        @JsonSubTypes.Type(value = ReRunWorkflowCommandParam.class, name = "REPEAT_RUNNING"),
        @JsonSubTypes.Type(value = RecoverFailureTaskCommandParam.class, name = "START_FAILURE_TASK_PROCESS"),
        @JsonSubTypes.Type(value = WorkflowFailoverCommandParam.class, name = "RECOVER_TOLERANCE_FAULT"),
})
public interface ICommandParam {

    /**
     * The task which need to be as the beginning of the workflow.
     */
    List<Long> getStartNodes();

    /**
     * The command params.
     */
    List<Property> getCommandParams();

    /**
     * Get the time zone.
     * todo: we should remove this field.
     */
    String getTimeZone();

    /**
     * Whether the command is used to trigger a sub workflow instance.
     */
    boolean isSubWorkflowInstance();

    /**
     * Get the command type.
     */
    CommandType getCommandType();

}
