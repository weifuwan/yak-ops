package io.yak.ops.api.dto;

import io.yak.ops.common.enums.ScheduleMissedFirePolicy;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * schedule parameters
 */
@Data
public class ScheduleParam {

    private Date startTime;
    private Date endTime;
    private String crontab;
    private String timezoneId;
    private ScheduleMissedFirePolicy missedFirePolicy = ScheduleMissedFirePolicy.FIRE_ALL_MISSED;

    @JsonIgnore
    private boolean missedFirePolicySet;

    public ScheduleParam() {
    }

    public ScheduleParam(Date startTime, Date endTime, String timezoneId, String crontab) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.timezoneId = timezoneId;
        this.crontab = crontab;
    }

    public void setMissedFirePolicy(ScheduleMissedFirePolicy missedFirePolicy) {
        this.missedFirePolicy = missedFirePolicy;
        this.missedFirePolicySet = true;
    }

    public boolean isMissedFirePolicySet() {
        return missedFirePolicySet;
    }

    @Override
    public String toString() {
        return "ScheduleParam{"
                + "startTime=" + startTime
                + ", endTime=" + endTime
                + ", crontab='" + crontab + '\''
                + ", timezoneId='" + timezoneId + '\''
                + '}';
    }
}
