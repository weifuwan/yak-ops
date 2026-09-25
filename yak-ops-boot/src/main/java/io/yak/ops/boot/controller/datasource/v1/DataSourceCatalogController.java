package io.yak.ops.boot.controller.datasource.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogBusiness;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.bean.vo.datasource.catalog.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.catalog.DataSourceCatalogTableVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供数据库、Schema、表和字段等 Catalog 元数据接口。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Tag(name = "数据源 Catalog 元数据接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequestMapping(DataSourceConstants.API_PREFIX + "/catalog")
public class DataSourceCatalogController {

    @Resource
    private DataSourceCatalogBusiness catalogBusiness;

    @Operation(summary = "查询数据库列表")
    @GetMapping("/{id}/databases")
    public Result<List<String>> databases(@PathVariable("id") String id) {
        return Result.success(catalogBusiness.queryDatabases(id));
    }

    @Operation(summary = "查询 Schema 列表")
    @GetMapping("/{id}/schemas")
    public Result<List<String>> schemas(
            @PathVariable("id") String id,
            @RequestParam(value = "database", required = false) String database) {
        return Result.success(catalogBusiness.querySchemas(id, database));
    }

    @Operation(summary = "查询表和视图列表")
    @GetMapping("/{id}/tables")
    public Result<List<DataSourceCatalogTableVO>> tables(
            @PathVariable("id") String id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(catalogBusiness.queryTables(id, database, schema, keyword));
    }

    @Operation(summary = "按关键字搜索表和视图")
    @GetMapping("/{id}/tables/search")
    public Result<List<DataSourceCatalogTableVO>> searchTables(
            @PathVariable("id") String id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(catalogBusiness.searchTables(id, database, schema, keyword, limit));
    }

    @Operation(summary = "查询表字段列表")
    @GetMapping("/{id}/columns")
    public Result<List<DataSourceCatalogColumnVO>> columns(
            @PathVariable("id") String id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam("table") String table) {
        return Result.success(catalogBusiness.queryColumns(id, database, schema, table));
    }
}
