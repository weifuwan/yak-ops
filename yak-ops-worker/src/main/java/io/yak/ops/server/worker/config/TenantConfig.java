package io.yak.ops.server.worker.config;

import lombok.Data;

@Data
public class TenantConfig {

    private boolean autoCreateTenantEnabled = true;
    private boolean defaultTenantEnabled = false;
}
