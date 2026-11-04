package io.yak.ops.api.service;

import io.yak.ops.api.enums.Status;
import io.yak.ops.common.constants.Constants;

import java.text.MessageFormat;
import java.util.Map;

public class BaseServiceTestTool {

    protected void putMsg(Map<String, Object> result, Status status, Object... statusParams) {
        result.put(Constants.STATUS, status);
        if (statusParams != null && statusParams.length > 0) {
            result.put(Constants.MSG, MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.put(Constants.MSG, status.getMsg());
        }
    }
}
