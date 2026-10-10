package io.yak.ops.boot.controller.task.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.task.definition.DefinitionService;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件无关的任务定义与可执行版本历史只读接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "任务定义")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-definitions")
public class TaskDefinitionController {

    @Resource
    private DefinitionService definitionService;

    @Operation(summary = "查询任务定义")
    @GetMapping("/{id}")
    public Result<DefinitionVO> detail(@PathVariable("id") String id) {
        return Result.success(definitionService.query(id));
    }

    @Operation(summary = "查询任务定义历史版本")
    @GetMapping("/{id}/versions")
    public Result<List<DefinitionVersionVO>> versions(@PathVariable("id") String id) {
        return Result.success(definitionService.queryVersions(id));
    }
}
