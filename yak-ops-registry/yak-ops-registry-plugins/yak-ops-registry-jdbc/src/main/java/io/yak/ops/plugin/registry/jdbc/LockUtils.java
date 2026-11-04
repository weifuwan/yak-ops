package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.common.utils.NetUtils;
import io.yak.ops.common.utils.OSUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LockUtils {

    private static final String LOCK_OWNER_PREFIX = NetUtils.getHost() + "_" + OSUtils.getProcessID() + "_";

    public static String getLockOwner() {
        return LOCK_OWNER_PREFIX + Thread.currentThread().getName();
    }

}
