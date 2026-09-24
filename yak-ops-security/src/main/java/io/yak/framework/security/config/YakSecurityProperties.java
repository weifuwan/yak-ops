package io.yak.framework.security.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Yak Security 模块配置。 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = YakSecurityProperties.PREFIX)
public class YakSecurityProperties {

  public static final String PREFIX = "yak.security";

  private boolean enabled = true;
  private boolean databaseEnabled = true;
  private boolean webEnabled = true;
  private boolean authenticationEnabled = true;

  private List<String> publicPaths = new ArrayList<String>(Arrays.asList(
          "/yak-security/api/v1/account/login",
          "/yak-security/api/v1/common/heart",
          "/v3/api-docs/**",
          "/swagger-ui/**",
          "/swagger-ui.html"
  ));

  private boolean auditEnabled = true;
  private String applicationName;

  private final DataSourceProperties datasource = new DataSourceProperties();
  private final AuthenticationProperties authentication = new AuthenticationProperties();
  private final BootstrapProperties bootstrap = new BootstrapProperties();
  private final PermissionRegistrationProperties permissionRegistration =
          new PermissionRegistrationProperties();
  private final PermissionCacheProperties permissionCache = new PermissionCacheProperties();
  private final LoginSecurityProperties login = new LoginSecurityProperties();

  /** Sa-Token login-state persistence. */
  public enum AuthenticationStorage {
    MEMORY,
    REDIS
  }

  @Getter
  @Setter
  @ToString
  public static class AuthenticationProperties {
    /** Maximum idle time for an authenticated Sa-Token login state. */
    private Duration idleTimeout = Duration.ofMinutes(30);

    /** Memory for local development, Redis for shared multi-instance login state. */
    private AuthenticationStorage storage = AuthenticationStorage.MEMORY;

    private final RedisStorageProperties redis = new RedisStorageProperties();
  }

  @Getter
  @Setter
  @ToString
  public static class RedisStorageProperties {
    private String host = "127.0.0.1";
    private int port = 6379;
    @ToString.Exclude
    private String password;
    private int database = 0;
    private int maxTotal = 64;
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

  public void validateDatabaseConfiguration() {
    if (!enabled || !databaseEnabled || !datasource.isEnabled()) {
      return;
    }
    requireText(applicationName, PREFIX + ".application-name");
    datasource.validate();
  }

  @Getter
  @Setter
  @ToString
  public static class DataSourceProperties {
    private boolean enabled = true;
    private String url;
    private String username;
    @ToString.Exclude
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int initialSize = 1;
    private int minIdle = 1;
    private int maxActive = 8;
    private long maxWait = 60_000L;
    private String validationQuery = "SELECT 1";
    private boolean testWhileIdle = true;
    private boolean testOnBorrow = false;
    private boolean testOnReturn = false;

    private void validate() {
      requireText(url, PREFIX + ".datasource.url");
      requireText(username, PREFIX + ".datasource.username");
      requireText(driverClassName, PREFIX + ".datasource.driver-class-name");

      if (initialSize < 0) {
        throw invalidProperty(PREFIX + ".datasource.initial-size",
                "must be greater than or equal to 0");
      }
      if (minIdle < 0) {
        throw invalidProperty(PREFIX + ".datasource.min-idle",
                "must be greater than or equal to 0");
      }
      if (maxActive <= 0) {
        throw invalidProperty(PREFIX + ".datasource.max-active", "must be greater than 0");
      }
      if (initialSize > maxActive) {
        throw invalidProperty(PREFIX + ".datasource.initial-size",
                "must not be greater than max-active");
      }
      if (minIdle > maxActive) {
        throw invalidProperty(PREFIX + ".datasource.min-idle",
                "must not be greater than max-active");
      }
      if (maxWait < -1L) {
        throw invalidProperty(PREFIX + ".datasource.max-wait",
                "must be -1 or greater than or equal to 0");
      }

      boolean connectionValidationEnabled = testWhileIdle || testOnBorrow || testOnReturn;
      if (connectionValidationEnabled && !StringUtils.hasText(validationQuery)) {
        throw invalidProperty(PREFIX + ".datasource.validation-query",
                "must not be blank when connection validation is enabled");
      }
    }
  }

  private static void requireText(String value, String key) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("Missing required configuration: " + key);
    }
  }

  private static IllegalStateException invalidProperty(String key, String message) {
    return new IllegalStateException("Invalid configuration: " + key + " " + message);
  }
}
