package io.yak.ops.boot.controller.datasource.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.DataSourceBusiness;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.PagingData;
import io.yak.ops.common.Result;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供数据源配置管理、列表查询和连接测试 HTTP 接口。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Tag(name = "数据源管理接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequestMapping(DataSourceConstants.API_PREFIX)
public class DataSourceController {

    @Resource
    private DataSourceBusiness dataSourceBusiness;

    @Operation(summary = "新增数据源")
    @PostMapping
    public Result<Boolean> create(@Valid @RequestBody DataSourceDTO dto) {
        return Result.success(dataSourceBusiness.addDataSource(dto));
    }

    @Operation(summary = "编辑数据源")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable("id") Long id, @Valid @RequestBody DataSourceDTO dto) {
        return Result.success(dataSourceBusiness.updateDataSource(id, dto));
    }

    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public Result<DataSourceVO> detail(@PathVariable("id") Long id) {
        return Result.success(dataSourceBusiness.queryDataSource(id));
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        return Result.success(dataSourceBusiness.deleteDataSource(id));
    }

    @Operation(summary = "分页查询数据源")
    @PostMapping("/page")
    public Result<PagingData<DataSourceVO>> page(@Valid @RequestBody DataSourceQueryDTO dto) {
        return Result.success(dataSourceBusiness.queryDataSourcePage(dto));
    }

    @Operation(summary = "查询数据源总览统计")
    @GetMapping("/summary")
    public Result<DataSourceSummaryVO> summary() {
        return Result.success(dataSourceBusiness.queryDataSourceSummary());
    }

    @Operation(summary = "查询全部数据源")
    @RequestMapping(
            value = "/all",
            method = {RequestMethod.GET, RequestMethod.POST})
    public Result<PagingData<DataSourceVO>> all() {
        return Result.success(dataSourceBusiness.queryAllDataSources());
    }

    @Operation(summary = "查询数据源下拉选项")
    @GetMapping("/option")
    public Result<List<DataSourceOptionVO>> option(@RequestParam(value = "dbType", required = false) String dbType) {
        return Result.success(dataSourceBusiness.queryDataSourceOptions(dbType));
    }

    @Operation(summary = "测试已保存数据源连接")
    @RequestMapping(
            value = "/{id}/connect-test",
            method = {RequestMethod.GET, RequestMethod.POST})
    public Result<Boolean> testConnection(@PathVariable("id") Long id) {
        return Result.success(dataSourceBusiness.testConnection(id));
    }

    @Operation(summary = "使用连接参数测试数据源连接")
    @PostMapping("/connect-test-with-param")
    public Result<Boolean> testConnection(@Valid @RequestBody DataSourceConnectTestDTO dto) {
        return Result.success(dataSourceBusiness.testConnection(dto));
    }
}
