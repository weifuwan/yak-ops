package io.yak.ops.boot.controller.datasource.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.plugin.DataSourcePluginBusiness;
import io.yak.ops.common.bean.vo.datasource.plugin.DataSourcePluginConfigVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供数据源插件动态表单配置。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Tag(name = "数据源表单配置接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequestMapping(DataSourceConstants.API_PREFIX + "/plugin/config")
public class DataSourcePluginConfigController {

    @Resource
    private DataSourcePluginBusiness pluginBusiness;

    @Operation(summary = "查询数据源动态表单配置")
    @GetMapping
    public Result<DataSourcePluginConfigVO> getPluginConfig(@RequestParam("pluginType") String pluginType) {
        return Result.success(pluginBusiness.queryPluginConfig(pluginType));
    }
}
