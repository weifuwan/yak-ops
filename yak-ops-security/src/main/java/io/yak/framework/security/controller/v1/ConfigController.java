package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.HttpRequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "配置管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/config")
public class ConfigController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "根据条件查询配置列表")
    @PostMapping("/list")
    public Result<List<ConfigVO>> list(
            @RequestBody(required = false) ConfigDTO condition) {

        normalizeConfig(condition);
        return Result.success(
                configService.queryByCondt(condition));
    }

    @Operation(summary = "分页查询配置")
    @PostMapping("/page")
    public Result<PagingData<ConfigVO>> page(
            @RequestBody(required = false) ConfigQueryDTO queryDTO) {

        ConfigQueryDTO query = normalizeQuery(queryDTO);
        PagingData<ConfigVO> pagingData =
                configService.pagingConfig(query);
        return Result.success(pagingData);
    }

    @Operation(summary = "查询全部配置分组")
    @GetMapping("/group/list")
    public Result<List<String>> groups() {
        return Result.success(configService.listGroups());
    }

    @Operation(summary = "根据配置 ID 查询配置详情")
    @GetMapping("/{id}")
    public Result<ConfigVO> detail(
            @PathVariable("id") Long configId) {

        return getConfigResult(configId);
    }

    @Operation(summary = "根据配置 ID 查询配置详情（兼容接口）")
    @GetMapping("/get")
    public Result<ConfigVO> get(
            @RequestParam("configId") Long configId) {

        return getConfigResult(configId);
    }

    @Operation(summary = "新增配置")
    @PostMapping
    public Result<Long> create(
            HttpServletRequest request,
            @RequestBody ConfigDTO configDTO) {

        return addConfig(request, configDTO);
    }

    @Operation(summary = "新增配置（兼容接口）")
    @PutMapping("/add")
    public Result<Long> add(
            HttpServletRequest request,
            @RequestBody ConfigDTO configDTO) {

        return addConfig(request, configDTO);
    }

    @Operation(summary = "编辑配置")
    @PutMapping
    public Result<Void> update(
            HttpServletRequest request,
            @RequestBody ConfigDTO configDTO) {

        return editConfig(request, configDTO);
    }

    @Operation(summary = "编辑配置（兼容接口）")
    @PostMapping("/edit")
    public Result<Void> edit(
            HttpServletRequest request,
            @RequestBody ConfigDTO configDTO) {

        return editConfig(request, configDTO);
    }

    @Operation(summary = "切换配置状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            HttpServletRequest request,
            @PathVariable("id") Long configId,
            @RequestBody ConfigDTO configDTO) {

        return configService.switchConfig(
                configId,
                configDTO == null ? null : configDTO.getStatus(),
                HttpRequestUtil.getOperator(request));
    }

    @Operation(summary = "切换配置状态（兼容接口）")
    @PostMapping("/switch")
    public Result<Void> switchConfig(
            HttpServletRequest request,
            @RequestBody ConfigDTO configDTO) {

        return configService.switchConfig(
                configDTO == null ? null : configDTO.getId(),
                configDTO == null ? null : configDTO.getStatus(),
                HttpRequestUtil.getOperator(request));
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(
            HttpServletRequest request,
            @PathVariable("id") Long configId) {

        return deleteConfig(request, configId);
    }

    @Operation(summary = "删除配置（兼容接口）")
    @DeleteMapping("/del")
    public Result<Void> delete(
            HttpServletRequest request,
            @RequestParam("id") Long configId) {

        return deleteConfig(request, configId);
    }

    private Result<ConfigVO> getConfigResult(Long configId) {
        if (configId == null) {
            return Result.buildParamIllegal("配置 ID 不能为空");
        }

        ConfigVO config = configService.getConfigById(configId);
        if (config == null) {
            return Result.buildNotExist("配置不存在");
        }

        return Result.success(config);
    }

    private Result<Long> addConfig(
            HttpServletRequest request,
            ConfigDTO configDTO) {

        normalizeConfig(configDTO);
        return configService.addConfig(
                configDTO,
                HttpRequestUtil.getOperator(request));
    }

    private Result<Void> editConfig(
            HttpServletRequest request,
            ConfigDTO configDTO) {

        if (configDTO == null) {
            return Result.buildParamIllegal("配置信息不能为空");
        }
        if (configDTO.getId() == null) {
            return Result.buildParamIllegal("配置 ID 不能为空");
        }
        if (configService.getConfigById(configDTO.getId()) == null) {
            return Result.buildNotExist("配置不存在");
        }

        normalizeConfig(configDTO);
        return configService.editConfig(
                configDTO,
                HttpRequestUtil.getOperator(request));
    }

    private Result<Void> deleteConfig(
            HttpServletRequest request,
            Long configId) {

        return configService.delConfig(
                configId,
                HttpRequestUtil.getOperator(request));
    }

    private ConfigQueryDTO normalizeQuery(ConfigQueryDTO queryDTO) {
        ConfigQueryDTO query = queryDTO == null
                ? new ConfigQueryDTO()
                : queryDTO;

        query.setPage(Math.max(1, query.getPage()));
        query.setSize(query.getSize() <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(query.getSize(), MAX_PAGE_SIZE));
        query.setValueGroup(trim(query.getValueGroup()));
        query.setValueName(trim(query.getValueName()));
        query.setMemo(trim(query.getMemo()));
        query.setOperator(trim(query.getOperator()));

        return query;
    }

    private void normalizeConfig(ConfigDTO configDTO) {
        if (configDTO == null) {
            return;
        }

        configDTO.setValueGroup(trim(configDTO.getValueGroup()));
        configDTO.setValueName(trim(configDTO.getValueName()));
        configDTO.setMemo(trim(configDTO.getMemo()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
