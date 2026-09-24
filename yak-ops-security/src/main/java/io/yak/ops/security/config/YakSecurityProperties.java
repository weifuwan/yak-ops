package io.yak.ops.security.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = YakSecurityProperties.PREFIX)
public class YakSecurityProperties {

  public static final String PREFIX = "yak.security";

  private List<String> publicPaths =
      new ArrayList<>(Arrays.asList(
          "/yak-security/api/v1/account/login",
          "/v3/api-docs/**",
          "/swagger-ui/**",
          "/swagger-ui.html"));

  private String applicationName;

  private final AuthenticationProperties authentication = new AuthenticationProperties();
  private final BootstrapProperties bootstrap = new BootstrapProperties();
  private final PermissionRegistrationProperties permissionRegistration =
      new PermissionRegistrationProperties();
  private final PermissionCacheProperties permissionCache = new PermissionCacheProperties();
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
  public static class PermissionCacheProperties {
    private boolean enabled = true;
    private long ttlMinutes = 20;
    private long maximumSize = 10_000;
  }

  @Getter
  @Setter
  @ToString
  public static class PermissionRegistrationProperties {
    private boolean enabled = true;
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
