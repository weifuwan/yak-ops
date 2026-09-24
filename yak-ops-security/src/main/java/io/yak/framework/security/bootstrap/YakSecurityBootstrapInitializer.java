package io.yak.framework.security.bootstrap;

import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.config.YakSecurityProperties;
import io.yak.framework.security.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Creates the first user for a newly installed application. */
public class YakSecurityBootstrapInitializer implements ApplicationRunner {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(YakSecurityBootstrapInitializer.class);
  private static final String BOOTSTRAP_OPERATOR = "yak-security-bootstrap";

  private final YakSecurityProperties properties;
  private final UserService userService;

  public YakSecurityBootstrapInitializer(
      YakSecurityProperties properties,
      UserService userService) {
    this.properties = properties;
    this.userService = userService;
  }

  @Override
  @Transactional(transactionManager = "yakSecurityTransactionManager", rollbackFor = Exception.class)
  public void run(ApplicationArguments args) {
    if (!userService.getAllUserBriefList().isEmpty()) return;

    YakSecurityProperties.BootstrapProperties bootstrap = properties.getBootstrap();
    requireText(bootstrap.getUsername(), "username");
    requireText(bootstrap.getPassword(), "password");
    requireText(bootstrap.getRealName(), "real-name");

    UserDTO user = new UserDTO();
    user.setUserName(bootstrap.getUsername());
    user.setPw(bootstrap.getPassword());
    user.setRealName(bootstrap.getRealName());

    Result<Void> result = userService.addUser(user, BOOTSTRAP_OPERATOR);
    if (result.failed()) {
      throw new IllegalStateException(
          "Cannot bootstrap Yak Security user: " + result.getMessage());
    }

    LOGGER.warn("Yak Security bootstrap user '{}' was created. "
        + "Disable yak.security.bootstrap.enabled now.", bootstrap.getUsername());
  }

  private static void requireText(String value, String property) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(
          "yak.security.bootstrap." + property
              + " must be configured when bootstrap is enabled");
    }
  }
}
