package io.yak.ops.server.worker.utils;

import io.yak.ops.common.constants.TenantConstants;
import io.yak.ops.common.utils.OSUtils;
import io.yak.ops.plugin.task.api.TaskException;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.server.worker.config.TenantConfig;
import io.yak.ops.server.worker.config.WorkerConfig;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class TenantUtils {

    public static boolean isTenantEnable() {
        // todo: add tenantEnable in workerConfig, the tenantEnable shouldn't judged by sudoEnable, these should be two
        // config
        return OSUtils.isSudoEnable();
    }

    /**
     * Get the the actual tenant code or create the tenant if it doesn't exist.
     * <p>
     * If sudo is enabled then will check tenant.
     * If the tenant code is the default tenant code and the {@link TenantConfig#isDefaultTenantEnabled()} is enabled, will return the bootstrap user.
     * If the tenant code is not the default tenant code, will check if the tenant exist, if the tenant is not exist and {@link TenantConfig#isAutoCreateTenantEnabled()} is true will create the tenant.
     * <p>
     * If sudo is not enabled, will not check the tenant code.
     */
    public static String getOrCreateActualTenant(WorkerConfig workerConfig, TaskExecutionContext taskExecutionContext) {
        TenantConfig tenantConfig = workerConfig.getTenantConfig();

        if (!isTenantEnable()) {
            log.info("Tenant is not enabled, will use the bootstrap: {} user as tenant", getBootstrapTenant());
            return getBootstrapTenant();
        }

        String tenantCode = taskExecutionContext.getTenantCode();
        if (isDefaultTenant(tenantCode)) {
            if (tenantConfig.isDefaultTenantEnabled()) {
                log.info("Current tenant is default tenant, will use bootstrap user: {} to execute the task",
                        getBootstrapTenant());
                return getBootstrapTenant();
            } else {
                throw new TaskException(
                        "The tenantCode is " + tenantCode + ", please enable TenantConfig#isDefaultTenantEnabled");
            }
        }

        if (tenantConfig.isAutoCreateTenantEnabled()) {
            OSUtils.createUserIfAbsent(tenantCode);
        }

        if (!tenantExists(tenantCode)) {
            throw new TaskException(String.format("TenantCode: %s doesn't exist", tenantCode));
        }
        return tenantCode;
    }

    public static boolean isDefaultTenant(String tenantCode) {
        return TenantConstants.DEFAULT_TENANT_CODE.equals(tenantCode);
    }

    public static String getBootstrapTenant() {
        return TenantConstants.BOOTSTRAP_SYSTEM_USER;
    }

    public static boolean isBootstrapTenant(String tenantCode) {
        return TenantConstants.BOOTSTRAP_SYSTEM_USER.equals(tenantCode);
    }

    public static boolean tenantExists(String tenantCode) {
        return OSUtils.getUserList().contains(tenantCode);
    }

}
