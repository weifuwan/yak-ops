package io.yak.ops.plugin.task.api;

import java.io.Serializable;

import lombok.Data;

@Data
public class SQLTaskExecutionContext implements Serializable {

    private int warningGroupId;

    private String connectionParams;

    private String defaultFS;

    @Override
    public String toString() {
        return "SQLTaskExecutionContext{"
                + "warningGroupId=" + warningGroupId
                + ", connectionParams='" + connectionParams + '\''
                + ", defaultFS='" + defaultFS + '\'' + '}';
    }
}
