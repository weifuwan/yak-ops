package io.yak.ops.security.config;

import io.yak.ops.security.constant.SecurityConstants;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Yak Security user/login configuration. */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = SecurityConstants.CONFIG_PREFIX)
public class YakSecurityProperties {

    private boolean enabled = true;
    private boolean databaseEnabled = true;
    private boolean webEnabled = true;
    private boolean authenticationEnabled = true;

    private List<String> publicPaths = new ArrayList<>(Arrays.asList(
            SecurityConstants.LOGIN_API_PATH, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));

    private String applicationName;

    private final AuthenticationProperties authentication = new AuthenticationProperties();
    private final BootstrapProperties bootstrap = new BootstrapProperties();
    private final LoginSecurityProperties login = new LoginSecurityProperties();

    @Getter
    @Setter
    @ToString
    public static class AuthenticationProperties {
        private Duration idleTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    @ToString
    public static class LoginSecurityProperties {
        private int maxFailureCount = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        private boolean hideAccountNotFound = true;
    }

    @Getter
    @Setter
    @ToString
    public static class BootstrapProperties {
        private boolean enabled = false;
        private String username = "admin";

        @ToString.Exclude
        private String password;

        private String realName = "系统管理员";
    }
}
