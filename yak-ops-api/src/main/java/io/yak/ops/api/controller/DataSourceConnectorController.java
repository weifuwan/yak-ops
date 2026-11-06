package io.yak.ops.api.controller;

import io.yak.ops.api.service.DataSourceCatalogService;
import io.yak.ops.api.enums.Status;
import io.yak.ops.plugin.datasource.api.utils.CommonUtils;
import io.yak.ops.api.service.DataSourceService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.plugin.datasource.api.datasource.BaseDataSourceParamDTO;
import io.yak.ops.plugin.datasource.api.datasource.ConnectionProperty;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.spi.datasource.ConnectionParam;
import io.yak.ops.spi.enums.DbType;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("datasources")
public class DataSourceConnectorController extends BaseController {

    private final DataSourceCatalogService catalogService;
    private final DataSourceService dataSourceService;

    public DataSourceConnectorController(DataSourceCatalogService catalogService, DataSourceService dataSourceService) {
        this.catalogService = catalogService;
        this.dataSourceService = dataSourceService;
    }

    @GetMapping("/kerberos-startup-state")
    public Result<Object> kerberosStartupState() {
        return success(Status.SUCCESS.getMsg(), CommonUtils.getKerberosStartupState());
    }

    @GetMapping("/connection-properties")
    public Result<List<ConnectionProperty>> getConnectionProperties(@RequestParam("type") DbType type) {
        return Result.success(catalogService.getConnectionProperties(type));
    }

    @PostMapping("/connect")
    public Result<Boolean> testConnection(@RequestBody String jsonStr) {
        BaseDataSourceParamDTO param = DataSourceUtils.buildDatasourceParam(jsonStr);
        DataSourceUtils.checkDatasourceParam(param);
        ConnectionParam connection = DataSourceUtils.buildConnectionParams(param);
        dataSourceService.checkConnection(param.getType(), connection);
        return Result.success(true);
    }
}
