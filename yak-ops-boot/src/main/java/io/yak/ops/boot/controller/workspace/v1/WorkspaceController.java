package io.yak.ops.boot.controller.workspace.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.workspace.WorkspaceService;
import io.yak.ops.business.workspace.constant.WorkspaceConstants;
import io.yak.ops.common.bean.dto.workspace.WorkspaceDTO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceMemberVO;
import io.yak.ops.common.bean.vo.workspace.WorkspaceVO;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
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
 * 对外提供 Workspace 创建、查询和成员关系 HTTP 接口。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Tag(name = "工作空间接口")
@RestController
@RequestMapping(WorkspaceConstants.API_PREFIX)
public class WorkspaceController {

    @Resource
    private WorkspaceService workspaceService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Operation(summary = "创建工作空间")
    @PostMapping
    public Result<WorkspaceVO> create(@Valid @RequestBody WorkspaceDTO dto) {
        return Result.success(workspaceService.createWorkspace(dto, authenticationManager.getLoginUserId()));
    }

    @Operation(summary = "查询当前用户的工作空间")
    @GetMapping
    public Result<List<WorkspaceVO>> list() {
        return Result.success(workspaceService.queryMyWorkspaces(authenticationManager.getLoginUserId()));
    }

    @Operation(summary = "查询工作空间详情")
    @GetMapping("/{id}")
    public Result<WorkspaceVO> detail(@PathVariable("id") String workspaceId) {
        return Result.success(workspaceService.queryWorkspace(workspaceId, authenticationManager.getLoginUserId()));
    }

    @Operation(summary = "查询工作空间成员关系")
    @GetMapping("/{id}/members")
    public Result<List<WorkspaceMemberVO>> members(@PathVariable("id") String workspaceId) {
        return Result.success(
                workspaceService.queryWorkspaceMembers(workspaceId, authenticationManager.getLoginUserId()));
    }
}
