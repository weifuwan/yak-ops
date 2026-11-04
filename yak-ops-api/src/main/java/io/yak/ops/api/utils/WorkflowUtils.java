package io.yak.ops.api.utils;

import io.yak.ops.api.dto.workflow.WorkflowBackFillRequest;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.utils.JSONUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WorkflowUtils {

    public static List<Long> parseStartNodeList(String startNodes) {
        try {
            if (StringUtils.isEmpty(startNodes)) {
                return new ArrayList<>();
            }
            return Arrays.stream(startNodes.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            throw new ServiceException("Parse startNodes: " + startNodes + " error", ex);
        }
    }

    public static WorkflowBackFillRequest.BackfillTime parseBackfillTime(String backfillTimeJson) {
        try {
            if (StringUtils.isEmpty(backfillTimeJson)) {
                throw new IllegalArgumentException("backfillTime is empty");
            }
            WorkflowBackFillRequest.BackfillTime backfillTime =
                    JSONUtils.parseObject(backfillTimeJson, WorkflowBackFillRequest.BackfillTime.class);
            if (backfillTime == null) {
                throw new IllegalArgumentException("backfillTime is invalid");
            }
            return backfillTime;
        } catch (Exception ex) {
            throw new ServiceException("Parse backfillTime: " + backfillTimeJson + " error", ex);
        }
    }

}
