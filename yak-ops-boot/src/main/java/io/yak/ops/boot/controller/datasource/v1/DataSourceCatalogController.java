package io.yak.ops.boot.controller.datasource.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.boot.controller.datasource.v1.converter.CatalogViewConverter;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.Result;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogDiagnosticsVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
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
    private DataSourceCatalogReader catalogReader;

    @Resource
    private CatalogViewConverter viewConverter;

    @Operation(summary = "查询 Catalog 运行诊断")
    @GetMapping("/diagnostics")
    public Result<DataSourceCatalogDiagnosticsVO> diagnostics() {
        return Result.success(viewConverter.diagnostics(catalogReader.diagnostics()));
    }

    @Operation(summary = "查询数据库列表")
    @GetMapping("/{id}/databases")
    public Result<List<String>> databases(@PathVariable("id") Long id) {
        return Result.success(catalogReader.listDatabases(id));
    }

    @Operation(summary = "查询 Schema 列表")
    @GetMapping("/{id}/schemas")
    public Result<List<String>> schemas(
            @PathVariable("id") Long id,
            @RequestParam(value = "database", required = false) String database) {
        return Result.success(catalogReader.listSchemas(id, database));
    }

    @Operation(summary = "查询表和视图列表")
    @GetMapping("/{id}/tables")
    public Result<List<DataSourceCatalogTableVO>> tables(
            @PathVariable("id") Long id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(catalogReader.listTables(id, database, schema, keyword).stream()
                .map(viewConverter::table)
                .toList());
    }

    @Operation(summary = "按关键字搜索表和视图")
    @GetMapping("/{id}/tables/search")
    public Result<List<DataSourceCatalogTableVO>> searchTables(
            @PathVariable("id") Long id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(catalogReader.searchTables(id, database, schema, keyword, limit).stream()
                .map(viewConverter::table)
                .toList());
    }

    @Operation(summary = "查询表字段列表")
    @GetMapping("/{id}/columns")
    public Result<List<DataSourceCatalogColumnVO>> columns(
            @PathVariable("id") Long id,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam("table") String table) {
        return Result.success(catalogReader.listColumns(id, database, schema, table).stream()
                .map(viewConverter::column)
                .toList());
    }

    @Operation(summary = "查询数据源表选项")
    @GetMapping("/list/{id}")
    public Result<List<DataSourceCatalogOptionVO>> listTable(@PathVariable("id") Long id) {
        return Result.success(catalogReader.listTable(id).stream().map(viewConverter::option).toList());
    }

    @Operation(summary = "按匹配模式查询数据源表")
    @GetMapping("/listByMatchMode/{id}")
    public Result<List<DataSourceCatalogOptionVO>> listTableReference(
            @PathVariable("id") Long id,
            @RequestParam(value = "matchMode", required = false) String matchMode,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(catalogReader.listTableReference(id, matchMode, keyword).stream()
                .map(viewConverter::option)
                .toList());
    }
}
