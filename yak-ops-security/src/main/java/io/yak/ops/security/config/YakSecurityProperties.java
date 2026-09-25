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

/**
 * 汇总 Yak Security 的启用开关、认证会话、登录保护和首用户初始化配置。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = SecurityConstants.CONFIG_PREFIX)
public class YakSecurityProperties {

    /** 是否启用 Yak Security 整体能力。 */
    private boolean enabled = true;

    /** 是否启用用户持久化和数据库相关安全能力。 */
    private boolean databaseEnabled = true;

    /** 是否启用 Security Web 入口和拦截器。 */
    private boolean webEnabled = true;

    /** 是否对受保护请求执行登录态校验。 */
    private boolean authenticationEnabled = true;

    /** 无需登录即可访问的请求路径模式。 */
    private List<String> publicPaths = new ArrayList<>(
            Arrays.asList(SecurityConstants.LOGIN_API_PATH, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));

    /** 用于隔离 Security 数据的应用标识。 */
    private String applicationName;

    /** HttpSession 认证会话参数。 */
    private final AuthenticationProperties authentication = new AuthenticationProperties();

    /** 首用户初始化参数。 */
    private final BootstrapProperties bootstrap = new BootstrapProperties();

    /** 登录失败保护参数。 */
    private final LoginSecurityProperties login = new LoginSecurityProperties();

    /**
     * HttpSession 登录态生命周期配置。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Getter
    @Setter
    @ToString
    public static class AuthenticationProperties {

        /** 会话无操作过期时间。 */
        private Duration idleTimeout = Duration.ofMinutes(30);
    }

    /**
     * 登录失败次数限制和临时锁定策略配置。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Getter
    @Setter
    @ToString
    public static class LoginSecurityProperties {

        /** 同一用户名或来源 IP 允许的连续失败次数。 */
        private int maxFailureCount = 5;

        /** 达到失败阈值后的临时锁定时长。 */
        private Duration lockDuration = Duration.ofMinutes(15);

        /** 是否对外隐藏“账号不存在”和“密码错误”的差异。 */
        private boolean hideAccountNotFound = true;
    }

    /**
     * 全新环境首个管理员账号的初始化配置。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    @Getter
    @Setter
    @ToString
    public static class BootstrapProperties {

        /** 是否在用户表为空时执行首用户初始化。 */
        private boolean enabled = false;

        /** 首个管理员账号用户名。 */
        private String username = "admin";

        /** 首个管理员账号原始密码，仅用于启动时创建账号。 */
        @ToString.Exclude
        private String password;

        /** 首个管理员账号展示姓名。 */
        private String realName = "系统管理员";
    }
}
