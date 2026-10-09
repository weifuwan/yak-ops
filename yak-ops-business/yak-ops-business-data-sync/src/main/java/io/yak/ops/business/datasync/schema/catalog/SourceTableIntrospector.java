package io.yak.ops.business.datasync.schema.catalog;

import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.business.datasync.schema.LogicalTable;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.util.StringUtils;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 从已保存 Datasource 的 Catalog 精确读取 Source Table，并归一为 LogicalTable。
 *
 * <p>Datasource Service 负责 Workspace / Credential / Plugin 边界；本组件只编排表与字段元数据读取，不接触连接参数。
 *
 * @author weifuwan
 * @since 2026-10-04
 */
@Component
public class SourceTableIntrospector {

    @Resource
    private DataSourceService dataSourceService;

    /**
     * 读取 Source Table 当前物理 Schema，并转换为 schemaVersion=1 的 LogicalTable。
     *
     * @param dataSourceId 已保存数据源 ID
     * @param database 数据库名称
     * @param schema Schema 名称
     * @param table 表名称
     * @return 归一后的逻辑表
     */
    public LogicalTable introspect(String dataSourceId, String database, String schema, String table) {
        if (StringUtils.isBlank(dataSourceId)) {
            throw new IllegalArgumentException("dataSourceId must not be blank");
        }
        if (StringUtils.isBlank(table)) {
            throw new IllegalArgumentException("table must not be blank");
        }

        DataSourceTablePathDTO path = new DataSourceTablePathDTO();
        path.setDatabase(StringUtils.trimToNull(database));
        path.setSchema(StringUtils.trimToNull(schema));
        path.setTable(table.trim());

        DataSourceCatalogTableVO tableMetadata = dataSourceService.queryCatalogTable(dataSourceId, path);
        List<DataSourceCatalogColumnVO> columns = dataSourceService.queryCatalogColumns(dataSourceId, path);
        return LogicalTableNormalizer.fromCatalog(tableMetadata, columns);
    }
}
