package io.yak.ops.security.bootstrap;

import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.result.Result;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.security.config.YakSecurityProperties;
import io.yak.ops.security.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在全新环境没有任何用户时，根据显式 Bootstrap 配置创建首个管理员账号。
 *
 * <p>只在启用 Bootstrap 且用户表为空时执行，创建成功后应关闭对应配置。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public class YakSecurityBootstrapInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(YakSecurityBootstrapInitializer.class);
    private static final String BOOTSTRAP_OPERATOR = "yak-security-bootstrap";

    private final YakSecurityProperties properties;
    private final UserService userService;

    public YakSecurityBootstrapInitializer(YakSecurityProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            throw new IllegalStateException("Cannot bootstrap Yak Security user: " + result.getMessage());
        }

        LOG.warn("已创建安全模块 Bootstrap 管理员，请关闭初始化配置，config=yak.security.bootstrap.enabled");
    }

    private static void requireText(String value, String property) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(
                    "yak.security.bootstrap." + property + " must be configured when bootstrap is enabled");
        }
    }
}
