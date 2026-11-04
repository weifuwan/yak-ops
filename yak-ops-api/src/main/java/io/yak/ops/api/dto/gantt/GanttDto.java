package io.yak.ops.api.dto.gantt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class GanttDto {

    /**
     * tasks list
     */
    private List<Task> tasks = new ArrayList<>();

    /**
     * task code list
     */
    private List<Long> taskNames;

    /**
     * task status map
     */
    private Map<String, String> taskStatus;

    public GanttDto() {
        this.taskStatus = new HashMap<>();
        taskStatus.put("success", "success");
    }

}
