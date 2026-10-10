package io.yak.ops.boot.controller.task.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.bean.dto.task.InstanceQueryDTO;
import io.yak.ops.common.bean.vo.task.AttemptVO;
import io.yak.ops.common.bean.vo.task.InstanceVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用Task Instance与Attempt历史只读入口，Workflow节点未来可直接复用。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Tag(name = "任务实例")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-instances")
public class TaskInstanceController {

    @Resource
    private InstanceService instanceService;

    @Operation(summary = "查询通用任务实例")
    @GetMapping("/{id}")
    public Result<InstanceVO> detail(@PathVariable("id") String id) {
        return Result.success(instanceService.query(id));
    }

    @Operation(summary = "分页查询通用任务实例")
    @PostMapping("/page")
    public Result<PagingData<InstanceVO>> page(@Valid @RequestBody InstanceQueryDTO query) {
        return Result.success(instanceService.queryPage(query));
    }

    @Operation(summary = "查询任务实例Attempt历史")
    @GetMapping("/{id}/attempts")
    public Result<List<AttemptVO>> attempts(@PathVariable("id") String id) {
        return Result.success(instanceService.queryAttempts(id));
    }
}
