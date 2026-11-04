package io.yak.ops.plugin.task.procedure;

import java.io.Serializable;

import lombok.Data;

/**
 *  master/worker task transport
 */
@Data
public class ProcedureTaskExecutionContext implements Serializable {

    private String connectionParams;

    @Override
    public String toString() {
        return "ProcedureTaskExecutionContext{"
                + "connectionParams='" + connectionParams + '\''
                + '}';
    }
}
