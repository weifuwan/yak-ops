package io.yak.ops.api.controller;

import io.yak.ops.api.service.DataSourceCatalogService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.api.vo.catalog.CatalogChildrenPageVO;
import io.yak.ops.api.vo.catalog.CatalogNodeType;
import io.yak.ops.api.vo.catalog.CatalogTreeNodeVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.datasource.api.catalog.CatalogCapabilities;
import io.yak.ops.plugin.datasource.api.catalog.CatalogIndex;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObject;
import io.yak.ops.plugin.datasource.api.catalog.CatalogObjectType;
import io.yak.ops.plugin.datasource.api.catalog.CatalogPath;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceCatalogColumn;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Unified metadata browsing endpoints for the data-development catalog. */
@Tag(name = "DATA_SOURCE_CATALOG_TAG")
@RestController
@RequestMapping("projects/{projectCode}/datasources/{datasourceId}/catalog")
public class DataSourceCatalogController extends BaseController {

    @Autowired
    private DataSourceCatalogService catalogService;

    @GetMapping("/capabilities")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogCapabilities", description = "Get datasource catalog capabilities")
    public Result<CatalogCapabilities> capabilities(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId) {
        return Result.success(catalogService.getCapabilities(loginUser, projectCode, datasourceId));
    }

    @GetMapping("/children")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogChildren", description = "Get direct children of a catalog tree node")
    public Result<CatalogChildrenPageVO> children(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId,
            @RequestParam(value = "nodeType", defaultValue = "DATASOURCE") CatalogNodeType nodeType,
            @RequestParam(value = "catalog", required = false) String catalog,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "objectName", required = false) String objectName,
            @RequestParam(value = "objectType", required = false) CatalogObjectType objectType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "100") Integer pageSize) {
        checkPageParams(pageNo, pageSize);
        CatalogPath path = new CatalogPath(catalog, database, schema, objectName, objectType);
        return Result.success(catalogService.getChildren(
                loginUser, projectCode, datasourceId, nodeType, path, keyword, pageNo, pageSize));
    }

    @GetMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogSearch", description = "Search datasource tables and views")
    public Result<List<CatalogTreeNodeVO>> search(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "100") Integer limit) {
        return Result.success(catalogService.search(loginUser, projectCode, datasourceId, keyword, limit));
    }

    @GetMapping("/object")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogObject", description = "Get database object details")
    public Result<CatalogObject> object(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId,
            @RequestParam(value = "catalog", required = false) String catalog,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam("objectName") String objectName,
            @RequestParam("objectType") CatalogObjectType objectType) {
        return Result.success(catalogService.getObject(loginUser, projectCode, datasourceId,
                new CatalogPath(catalog, database, schema, objectName, objectType)));
    }

    @GetMapping("/columns")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogColumns", description = "Get columns for a database object")
    public Result<List<DataSourceCatalogColumn>> columns(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId,
            @RequestParam(value = "catalog", required = false) String catalog,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam("objectName") String objectName,
            @RequestParam("objectType") CatalogObjectType objectType) {
        return Result.success(catalogService.getColumns(loginUser, projectCode, datasourceId,
                new CatalogPath(catalog, database, schema, objectName, objectType)));
    }

    @GetMapping("/indexes")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "catalogIndexes", description = "Get indexes for a database object")
    public Result<List<CatalogIndex>> indexes(
            @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User loginUser,
            @PathVariable("projectCode") long projectCode,
            @PathVariable("datasourceId") Integer datasourceId,
            @RequestParam(value = "catalog", required = false) String catalog,
            @RequestParam(value = "database", required = false) String database,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "objectType", required = false) CatalogObjectType objectType) {
        return Result.success(catalogService.getIndexes(loginUser, projectCode, datasourceId,
                new CatalogPath(catalog, database, schema, objectName, objectType)));
    }
}
