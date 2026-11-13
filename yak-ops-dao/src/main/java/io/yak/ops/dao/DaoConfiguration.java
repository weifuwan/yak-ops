package io.yak.ops.dao;

import io.yak.ops.dao.plugin.api.DaoPluginConfiguration;
import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;
import io.yak.ops.common.context.ProjectCodeContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.sf.jsqlparser.expression.LongValue;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@Configuration
@ComponentScan("io.yak.ops.dao")
@EnableAutoConfiguration
@MapperScan(basePackages = "io.yak.ops.dao.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class DaoConfiguration {

    private static final Set<String> MODELING_TENANT_TABLES = new HashSet<>(Arrays.asList(
            "t_yak_ops_business_type",
            "t_yak_ops_business_type_related_data_domain",
            "t_yak_ops_dw_layer",
            "t_yak_ops_data_domain",
            "t_yak_ops_business_process",
            "t_yak_ops_data_mart",
            "t_yak_ops_subject_domain",
            "t_yak_ops_dimension_table",
            "t_yak_ops_dimension_table_fields",
            "t_yak_ops_standard_directory",
            "t_yak_ops_field_standard",
            "t_yak_ops_code_standard",
            "t_yak_ops_code_detail",
            "t_yak_ops_unit_standard",
            "t_yak_ops_dictionary_standard",
            "t_yak_ops_indicator_atom",
            "t_yak_ops_indicator_derivative",
            "t_yak_ops_indicator_compose",
            "t_yak_ops_indicator_modifier",
            "t_yak_ops_time_period_category",
            "t_yak_ops_time_period",
            "t_yak_ops_version_management"
    ));

    /**
     * Inject this field to make sure the database is initialized, this can solve the table not found issue #8432.
     */
    @Autowired(required = false)
    public DataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer;

    /**
     * Inject this field to make sure the DaoPluginConfiguration is initialized before SpringConnectionFactory.
     */
    @Autowired
    public DaoPluginConfiguration daoPluginConfiguration;

    @Bean
    public MybatisPlusInterceptor paginationInterceptor(DbType dbType) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public net.sf.jsqlparser.expression.Expression getTenantId() {
                Long projectCode = ProjectCodeContext.getProjectCode();
                if (projectCode == null) {
                    throw new IllegalStateException(
                            "Project context is required when querying modeling tables");
                }
                return new LongValue(projectCode);
            }

            @Override
            public String getTenantIdColumn() {
                return "project_code";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return tableName == null
                        || !MODELING_TENANT_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT));
            }
        }));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        return new VendorDatabaseIdProvider();
    }

    @Bean
    public DbType dbType() {
        return daoPluginConfiguration.dbType();
    }

    @Bean
    public DatabaseMonitor databaseMonitor() {
        return daoPluginConfiguration.databaseMonitor();
    }

    @Bean
    public DatabaseDialect databaseDialect() {
        return daoPluginConfiguration.databaseDialect();
    }

}
