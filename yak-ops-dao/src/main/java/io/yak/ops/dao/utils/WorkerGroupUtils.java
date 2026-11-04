package io.yak.ops.dao.utils;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.WorkerGroup;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

public class WorkerGroupUtils {

    private static final String DEFAULT_WORKER_GROUP = "default";

    /**
     * Check if the worker group is empty, if the worker group is default, it is considered empty
     */
    public static boolean isWorkerGroupEmpty(String workerGroup) {
        return StringUtils.isEmpty(workerGroup) || getDefaultWorkerGroup().equals(workerGroup);
    }

    public static String getWorkerGroupOrDefault(String workerGroup) {
        return getWorkerGroupOrDefault(workerGroup, getDefaultWorkerGroup());
    }

    public static String getWorkerGroupOrDefault(String workerGroup, String defaultWorkerGroup) {
        return isWorkerGroupEmpty(workerGroup) ? defaultWorkerGroup : workerGroup;
    }

    public static String getDefaultWorkerGroup() {
        return DEFAULT_WORKER_GROUP;
    }

    public static List<String> getWorkerAddressListFromWorkerGroup(WorkerGroup workerGroup) {
        String addrList = workerGroup.getAddrList();
        if (StringUtils.isEmpty(addrList)) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(addrList.split(Constants.COMMA));
    }

}
