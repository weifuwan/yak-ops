package io.yak.ops.api.security.impl.oidc;

import java.util.List;

import lombok.Data;

/**
 * OIDC user configuration
 */
@Data
public class OidcUserConfig {

    /**
     * Whether to auto-create users if they don't exist
     */
    private boolean autoCreate = false;

    /**
     * Default tenant code for auto-created users
     */
    private String defaultTenantCode = "default";

    /**
     * Default queue for auto-created users
     */
    private String defaultQueue = "default";

    /**
     * Groups that map to an administrator role
     */
    private List<String> adminGroupMapping;
}
