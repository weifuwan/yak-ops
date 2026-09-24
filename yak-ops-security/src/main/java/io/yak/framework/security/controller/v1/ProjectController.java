package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.project.ProjectQueryDTO;
import io.yak.framework.security.common.dto.project.ProjectSaveDTO;
import io.yak.framework.security.common.dto.project.ProjectStatusDTO;
import io.yak.framework.security.common.dto.project.ProjectUserAssignDTO;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.project.ProjectDeleteCheckVO;
import io.yak.framework.security.common.vo.project.ProjectVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.util.HttpRequestUtil;
import io.yak.framework.security.web.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "项目管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/project")
@RequiresPermission(SecurityPermissionCode.Project.READ)
@YakPermission(
        code = SecurityPermissionCode.Project.READ,
        name = "查看授权项目",
        group = SecurityPermissionCode.GROUP_NAME,
        groupCode = SecurityPermissionCode.GROUP_CODE,
        menuCode = SecurityPermissionCode.Project.MENU_CODE,
        description = "查看工作空间列表及详情")
public class ProjectController {

  private final ProjectService projectService;

  /**
   * 创建项目管理接口。
   *
   * @param projectService 项目服务
   */
  public ProjectController(
          ProjectService projectService) {

    this.projectService = projectService;
  }

  /**
   * 根据项目 ID 查询项目详情。
   *
   * @param projectId 项目 ID
   * @return 项目详情
   */
  @Operation(summary = "根据项目 ID 查询项目详情")
  @GetMapping("/{id}")
  public Result<ProjectVO> detail(
          @PathVariable("id") Long projectId) {

    return Result.success(
            projectService
                    .getProjectDetailByProjectId(
                            projectId));

  }

  /**
   * 校验项目是否存在。
   *
   * @param projectId 项目 ID
   * @return 项目是否存在
   */
  @Operation(summary = "校验项目是否存在")
  @GetMapping("/{id}/exist")
  public Result<Boolean> checkExist(
          @PathVariable("id") Long projectId) {

    return Result.success(
            projectService.checkProjectExist(
                    projectId));
  }

  /**
   * 切换项目状态。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @return 状态切换结果
   */
  @Operation(summary = "切换项目状态")
  @PutMapping("/switch/{id}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> switchStatus(
          HttpServletRequest request,
          @PathVariable("id") Long projectId) {

    projectService.changeProjectStatus(
            projectId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 按目标值更新项目状态。
   *
   * <p>与切换接口不同，本接口具备幂等语义，适用于前端开关控件和请求重试。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param statusDTO 目标状态
   * @return 状态更新结果
   */
  @Operation(summary = "按目标值更新项目状态")
  @PutMapping("/{id}/status")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> updateStatus(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @RequestBody ProjectStatusDTO statusDTO) {

    if (statusDTO == null
            || statusDTO.getRunning() == null) {

      throw new IllegalArgumentException(
              "项目状态不能为空");
    }

    ProjectVO project =
            projectService
                    .getProjectDetailByProjectId(
                            projectId);

    if (!Objects.equals(
            project.getRunning(),
            statusDTO.getRunning())) {

      projectService.changeProjectStatus(
              projectId,
              HttpRequestUtil.getOperator(request));
    }

    return Result.success(null);
  }

  /**
   * 更新项目。
   *
   * @param request HTTP 请求
   * @param projectSaveDTO 项目信息
   * @return 更新结果
   */
  @Operation(summary = "更新项目")
  @PutMapping
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> update(
          HttpServletRequest request,
          @RequestBody
                  ProjectSaveDTO projectSaveDTO) {

    projectService.updateProject(
            projectSaveDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 创建项目。
   *
   * @param request HTTP 请求
   * @param projectSaveDTO 项目信息
   * @return 创建后的项目详情
   */
  @Operation(summary = "创建项目")
  @PostMapping
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<ProjectVO> create(
          HttpServletRequest request,
          @RequestBody
                  ProjectSaveDTO projectSaveDTO) {

    return Result.success(
            projectService.createProject(
                    projectSaveDTO,
                    HttpRequestUtil.getOperator(
                            request)));

  }

  /**
   * 执行项目删除前校验。
   *
   * @param projectId 项目 ID
   * @return 删除校验结果
   */
  @Operation(summary = "执行项目删除前校验")
  @GetMapping("/delete/check/{id}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<ProjectDeleteCheckVO> deleteCheck(
          @PathVariable("id") Long projectId) {

    return Result.success(
            projectService.checkBeforeDelete(
                    projectId));
  }

  /**
   * 根据项目 ID 删除项目。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @return 删除结果
   */
  @Operation(summary = "根据项目 ID 删除项目")
  @DeleteMapping("/{id}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> delete(
          HttpServletRequest request,
          @PathVariable("id") Long projectId) {

    projectService.deleteProjectByProjectId(
            projectId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 分页查询项目。
   *
   * @param queryDTO 查询条件
   * @return 项目分页结果
   */
  @Operation(summary = "分页查询项目")
  @PostMapping("/page")
  public Result<PagingData<ProjectVO>> page(
          @RequestBody ProjectQueryDTO queryDTO) {

    PagingData<ProjectVO> pagingData =
            projectService.getProjectPage(
                    queryDTO);

    return Result.success(pagingData);
  }

  /**
   * 查询全部项目简要信息。
   *
   * @return 项目简要信息列表
   */
  @Operation(summary = "查询全部项目简要信息")
  @GetMapping("/list")
  public Result<List<ProjectBriefVO>> list() {
    return Result.success(
            projectService.getProjectBriefList());
  }

  /**
   * 全量更新项目负责人。
   *
   * <p>用户列表为空时，清空项目的全部负责人。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param assignDTO 用户分配参数
   * @return 更新结果
   */
  @Operation(summary = "全量更新项目负责人")
  @PutMapping("/{id}/owners")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> replaceProjectOwners(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @RequestBody ProjectUserAssignDTO assignDTO) {

    ProjectSaveDTO projectSaveDTO =
            buildRelationUpdateDTO(projectId);

    projectSaveDTO.setOwnerIdList(
            assignDTO == null
                    ? null
                    : assignDTO.getUserIdList());

    projectService.updateProject(
            projectSaveDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);
  }

  /**
   * 全量更新项目成员。
   *
   * <p>用户列表为空时，清空项目的全部普通成员。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param assignDTO 用户分配参数
   * @return 更新结果
   */
  @Operation(summary = "全量更新项目成员")
  @PutMapping("/{id}/users")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> replaceProjectUsers(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @RequestBody ProjectUserAssignDTO assignDTO) {

    ProjectSaveDTO projectSaveDTO =
            buildRelationUpdateDTO(projectId);

    projectSaveDTO.setUserIdList(
            assignDTO == null
                    ? null
                    : assignDTO.getUserIdList());

    projectService.updateProject(
            projectSaveDTO,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);
  }

  /**
   * 添加项目负责人。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @return 添加结果
   */
  @Operation(summary = "添加项目负责人")
  @PutMapping("/{id}/owner/{ownerId}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> addProjectOwner(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @PathVariable Long ownerId) {

    projectService.addProjectOwner(
            projectId,
            ownerId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 删除项目负责人。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @return 删除结果
   */
  @Operation(summary = "删除项目负责人")
  @DeleteMapping("/{id}/owner/{ownerId}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> deleteProjectOwner(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @PathVariable Long ownerId) {

    projectService.delProjectOwner(
            projectId,
            ownerId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 添加项目用户。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @return 添加结果
   */
  @Operation(summary = "添加项目用户")
  @PutMapping("/{id}/user/{userId}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> addProjectUser(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @PathVariable Long userId) {

    projectService.addProjectUser(
            projectId,
            userId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 删除项目用户。
   *
   * @param request HTTP 请求
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @return 删除结果
   */
  @Operation(summary = "删除项目用户")
  @DeleteMapping("/{id}/user/{userId}")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<Void> deleteProjectUser(
          HttpServletRequest request,
          @PathVariable("id") Long projectId,
          @PathVariable Long userId) {

    projectService.delProjectUser(
            projectId,
            userId,
            HttpRequestUtil.getOperator(request));

    return Result.success(null);

  }

  /**
   * 查询项目未分配用户。
   *
   * @param projectId 项目 ID
   * @return 未分配用户列表
   */
  @Operation(summary = "查询项目未分配用户")
  @GetMapping("/unassigned")
  @RequiresPermission(SecurityPermissionCode.ROOT)
  public Result<List<UserBriefVO>> unassigned(
          @RequestParam("id") Long projectId) {

    return projectService
            .unassignedByProjectId(
                    projectId);

  }

  /**
   * 根据用户 ID 查询项目简要信息。
   *
   * @param userId 用户 ID
   * @return 项目简要信息列表
   */
  @Operation(summary = "根据用户 ID 查询项目简要信息")
  @GetMapping("/user/{userId}")
  public Result<List<ProjectBriefVO>>
  getProjectBriefByUserId(
          @PathVariable Long userId) {

    return projectService
            .getProjectBriefByUserId(
                    userId);
  }

  /**
   * 构建仅更新用户关系所需的项目参数。
   */
  private ProjectSaveDTO buildRelationUpdateDTO(
          Long projectId) {

    ProjectVO project =
            projectService
                    .getProjectDetailByProjectId(
                            projectId);

    ProjectSaveDTO projectSaveDTO =
            new ProjectSaveDTO();

    projectSaveDTO.setId(projectId);
    projectSaveDTO.setProjectName(
            project.getProjectName());

    return projectSaveDTO;
  }
}
