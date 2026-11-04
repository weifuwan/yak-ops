package io.yak.ops.api.dto.gantt;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import lombok.Data;

@Data
public class Task {

    private String taskName;

    /**
     * task start date
     */
    private List<Long> startDate = new LinkedList<>();
    /**
     * task end date
     */
    private List<Long> endDate = new LinkedList<>();

    /**
     * task execution date
     */
    private Date executionDate;

    /**
     * task iso start
     */
    private Date isoStart;

    /**
     * task iso end
     */
    private Date isoEnd;

    /**
     * task status
     */
    private String status;

    /**
     * task duration
     */
    private String duration;

}
