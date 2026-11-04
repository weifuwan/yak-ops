package io.yak.ops.common.utils.placeholder;

import static io.yak.ops.common.constants.DateConstants.PARAMETER_FORMAT_DATE;
import static io.yak.ops.common.constants.DateConstants.PARAMETER_FORMAT_TIME;
import static io.yak.ops.common.utils.DateUtils.format;
import static org.apache.commons.lang3.time.DateUtils.addDays;

import io.yak.ops.common.constants.DateConstants;
import io.yak.ops.common.enums.CommandType;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BusinessTimeUtils {

    private BusinessTimeUtils() {
        throw new IllegalStateException("BusinessTimeUtils class");
    }

    /**
     * get business time in parameters by different command types
     *
     * @param commandType command type
     * @param runTime     run time or schedule time
     * @return business time
     */
    public static Map<String, String> getBusinessTime(CommandType commandType, Date runTime, String timezone) {
        Date businessDate = runTime;
        Map<String, String> result = new HashMap<>();
        switch (commandType) {
            case COMPLEMENT_DATA:
                if (runTime == null) {
                    return result;
                }
            case START_PROCESS:
            case START_CURRENT_TASK_PROCESS:
            case RECOVER_TOLERANCE_FAULT_PROCESS:
            case RECOVER_SUSPENDED_PROCESS:
            case START_FAILURE_TASK_PROCESS:
            case REPEAT_RUNNING:
            case SCHEDULER:
            default:
                businessDate = addDays(new Date(), -1);
                if (runTime != null) {
                    /**
                     * If there is a scheduled time, take the scheduling time. Recovery from failed nodes, suspension of recovery, re-run for scheduling
                     */
                    businessDate = addDays(runTime, -1);
                }
                break;
        }
        Date businessCurrentDate = addDays(businessDate, 1);
        result.put(DateConstants.PARAMETER_CURRENT_DATE, format(businessCurrentDate, PARAMETER_FORMAT_DATE, timezone));
        result.put(DateConstants.PARAMETER_BUSINESS_DATE, format(businessDate, PARAMETER_FORMAT_DATE, timezone));
        result.put(DateConstants.PARAMETER_DATETIME, format(businessCurrentDate, PARAMETER_FORMAT_TIME, timezone));
        return result;
    }
}
