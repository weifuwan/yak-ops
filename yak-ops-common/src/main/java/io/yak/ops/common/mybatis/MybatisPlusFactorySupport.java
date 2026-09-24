package io.yak.ops.common.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import org.apache.ibatis.type.JdbcType;

/** Yak Ops 各业务数据源共享的 MyBatis-Plus 会话工厂配置。 */
public final class MybatisPlusFactorySupport {

    private MybatisPlusFactorySupport() {}

    /** 创建统一的 MyBatis 配置，避免各业务模块出现配置漂移。 */
    public static MybatisConfiguration createConfiguration() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.setCacheEnabled(false);
        return configuration;
    }

    /** 创建关闭启动横幅的全局配置。每个 SqlSessionFactory 使用独立实例。 */
    public static GlobalConfig createGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        return globalConfig;
    }
}
