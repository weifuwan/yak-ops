package io.yak.ops.api.security.impl.oidc;

import lombok.Data;

/**
 * OIDC provider configuration
 */
@Data
public class OidcProviderConfig {

    /**
     * Display the name for the provider (to show on the login button)
     */
    private String displayName;

    private String issuerUri;

    /**
     * URI for the provider's icon (logo) to be displayed on the login button.
     */
    private String iconUri;

    private String clientId;

    private String clientSecret;

    /**
     * OIDC client authentication method (e.g., client_secret_basic, client_secret_post)
     */
    private String clientAuthenticationMethod = "client_secret_basic";

    /**
     * OIDC scopes (comma-separated)
     */
    private String scope = "openid,profile,email";

    /**
     * OIDC username attribute (claim to use as username)
     */
    private String userNameAttribute = "preferred_username";

    /**
     * OIDC groups claim (claim containing user groups/roles)
     */
    private String groupsClaim;
}
