package io.yak.ops.boot.controller;

import io.yak.ops.business.schedule.ScheduleService;
import io.yak.ops.common.bean.dto.schedule.CreateScheduleDTO;
import io.yak.ops.common.bean.dto.schedule.SchedulePreviewDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleQueryDTO;
import io.yak.ops.common.bean.dto.schedule.UpdateScheduleDTO;
import io.yak.ops.common.bean.vo.schedule.SchedulePreviewVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleSummaryVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated schedule configuration and stateless preview endpoints. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/schedules")
public class ScheduleController {
    @Resource
    private ScheduleService service;

    @Resource
    private AuthenticationManager authenticationManager;

    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody CreateScheduleDTO dto) {
        return Result.success(
                service.create(WorkspaceContext.requireWorkspaceId(), dto, authenticationManager.getLoginUserId()));
    }

    @GetMapping("/{id}")
    public Result<ScheduleVO> detail(@PathVariable String id) {
        return Result.success(service.query(WorkspaceContext.requireWorkspaceId(), id));
    }

    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@PathVariable String id, @Valid @RequestBody UpdateScheduleDTO dto) {
        return Result.success(
                service.update(WorkspaceContext.requireWorkspaceId(), id, dto, authenticationManager.getLoginUserId()));
    }

    @PostMapping("/page")
    public Result<PagingData<ScheduleSummaryVO>> page(@Valid @RequestBody ScheduleQueryDTO dto) {
        return Result.success(PagingData.from(service.queryPage(WorkspaceContext.requireWorkspaceId(), dto)));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id, @RequestParam long revision) {
        service.delete(WorkspaceContext.requireWorkspaceId(), id, revision, authenticationManager.getLoginUserId());
        return Result.success(true);
    }

    @PostMapping("/preview")
    public Result<SchedulePreviewVO> preview(@Valid @RequestBody SchedulePreviewDTO dto) {
        WorkspaceContext.requireWorkspaceId();
        return Result.success(service.preview(dto));
    }
}
