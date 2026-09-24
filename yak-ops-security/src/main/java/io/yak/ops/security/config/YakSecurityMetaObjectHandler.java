package io.yak.ops.security.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.util.StringUtils;

/**
 * Yak Security 公共字段自动填充器。
 *
 * <p>负责在新增数据时填充应用隔离字段 appName。</p>
 *
 * @author weifuwan
 */
public final class YakSecurityMetaObjectHandler
        implements MetaObjectHandler {

    private final String applicationName;

    public YakSecurityMetaObjectHandler(
            String applicationName) {

        if (!StringUtils.hasText(applicationName)) {
            throw new IllegalArgumentException(
                    "applicationName must not be blank");
        }

        this.applicationName = applicationName;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(
                metaObject,
                "appName",
                String.class,
                applicationName
        );
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // appName 是数据归属标识，更新时不允许自动修改。
    }
}