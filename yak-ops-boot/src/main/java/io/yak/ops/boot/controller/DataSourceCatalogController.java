package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.common.bean.dto.datasource.DataSourceCatalogQueryDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourcePreviewDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePageDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceTablePathDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePreviewVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceTablePageVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes physical database, schema, table and column metadata for Workspace-owned datasources. */
@Tag(name = "数据源管理接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-source")
public class DataSourceCatalogController {

    @Resource
    private DataSourceCatalogService catalogService;

    @Operation(summary = "查询数据源可见数据库")
    @GetMapping("/{id}/catalog/databases")
    public Result<List<String>> catalogDatabases(@PathVariable("id") String id) {
        return Result.success(catalogService.listDatabases(id));
    }

    @Operation(summary = "查询数据源可见Schema")
    @GetMapping("/{id}/catalog/schemas")
    public Result<List<String>> catalogSchemas(
            @PathVariable("id") String id, @RequestParam(value = "database", required = false) String database) {
        return Result.success(catalogService.listSchemas(id, database));
    }

    @Operation(summary = "查询数据源表元数据")
    @PostMapping("/{id}/catalog/tables")
    public Result<List<DataSourceCatalogTableVO>> catalogTables(
            @PathVariable("id") String id, @Valid @RequestBody DataSourceCatalogQueryDTO dto) {
        return Result.success(catalogService.listTables(id, dto));
    }

    @Operation(summary = "分页查询数据源库表")
    @PostMapping("/{id}/catalog/table-page")
    public Result<DataSourceTablePageVO> catalogTablePage(
            @PathVariable("id") String id, @Valid @RequestBody DataSourceTablePageDTO dto) {
        return Result.success(catalogService.listTablePage(id, dto));
    }

    @Operation(summary = "预览未保存的来源读取配置")
    @PostMapping("/{id}/catalog/preview")
    public Result<DataSourcePreviewVO> preview(
            @PathVariable("id") String id, @Valid @RequestBody DataSourcePreviewDTO dto) {
        return Result.success(catalogService.preview(id, dto));
    }

    @Operation(summary = "查询数据源字段元数据")
    @PostMapping("/{id}/catalog/columns")
    public Result<List<DataSourceCatalogColumnVO>> catalogColumns(
            @PathVariable("id") String id, @Valid @RequestBody DataSourceTablePathDTO dto) {
        return Result.success(catalogService.listColumns(id, dto));
    }
}
