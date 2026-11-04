package io.yak.ops.plugin.task.api.model;

import java.util.Date;

import lombok.Data;

@Data
public class DateInterval {

    private Date startTime;
    private Date endTime;

    public DateInterval(Date beginTime, Date endTime) {
        this.startTime = beginTime;
        this.endTime = endTime;
    }
}
