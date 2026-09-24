package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.constant.SecurityPermissionCode;
import io.yak.framework.security.common.dto.dept.DeptDTO;
import io.yak.framework.security.common.dto.dept.DeptSaveDTO;
import io.yak.framework.security.common.vo.dept.DeptDeleteCheckVO;
import io.yak.framework.security.common.vo.dept.DeptTreeVO;
import io.yak.framework.security.common.vo.dept.DeptVO;
import io.yak.framework.security.permission.YakPermission;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.web.RequiresPermission;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部门管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "部门管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/dept")
@RequiresPermission(SecurityPermissionCode.Department.READ)
@YakPermission(
        code = SecurityPermissionCode.Department.READ,
        name = "查看部门管理",
        group = SecurityPermissionCode.GROUP_NAME,
        groupCode = SecurityPermissionCode.GROUP_CODE,
        menuCode = SecurityPermissionCode.Department.MENU_CODE,
        description = "查看部门树及部门详情")
public class DeptController {

  private final DeptService deptService;

  /**
   * 创建部门管理接口。
   *
   * @param deptService 部门服务
   */
  public DeptController(DeptService deptService) {
    this.deptService = deptService;
  }

  /**
   * 查询完整部门树。
   *
   * @return 部门树
   */
  @Operation(summary = "查询完整部门树")
  @GetMapping("/tree")
  public Result<DeptTreeVO> tree() {
    return Result.success(
            deptService.buildDeptTree());
  }

  /**
   * 根据部门 ID 查询部门详情。
   *
   * @param deptId 部门标识
   * @return 部门详情
   */
  @Operation(summary = "根据部门 ID 查询部门详情")
  @GetMapping("/{id}")
  public Result<DeptVO> detail(
          @PathVariable("id") Long deptId) {

    return Result.success(
            deptService.getDeptDetail(deptId));
  }

  /**
   * 新增部门。
   *
   * @param deptSaveDTO 部门信息
   * @return 新增结果
   */
  @Operation(summary = "新增部门")
  @PostMapping
  @RequiresPermission(SecurityPermissionCode.Department.CREATE)
  @YakPermission(
          code = SecurityPermissionCode.Department.CREATE,
          name = "新增部门",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Department.MENU_CODE,
          description = "创建根部门或子部门")
  public Result<Void> create(
          @RequestBody DeptSaveDTO deptSaveDTO) {

    deptService.createDept(deptSaveDTO);

    return Result.success(null);
  }

  /**
   * 编辑部门。
   *
   * @param deptSaveDTO 部门信息
   * @return 编辑结果
   */
  @Operation(summary = "编辑部门")
  @PutMapping
  @RequiresPermission(SecurityPermissionCode.Department.EDIT)
  @YakPermission(
          code = SecurityPermissionCode.Department.EDIT,
          name = "编辑部门",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Department.MENU_CODE,
          description = "修改部门名称、描述及上级部门")
  public Result<Void> update(
          @RequestBody DeptSaveDTO deptSaveDTO) {

    deptService.updateDept(deptSaveDTO);

    return Result.success(null);
  }

  /**
   * 删除部门前检查关联数据。
   *
   * <p>保留 DELETE 请求方式，与现有前端调用保持一致。
   *
   * @param deptId 部门标识
   * @return 删除检查结果
   */
  @Operation(summary = "删除部门前检查关联数据")
  @DeleteMapping("/delete/check/{id}")
  @RequiresPermission(SecurityPermissionCode.Department.DELETE)
  public Result<DeptDeleteCheckVO> checkBeforeDelete(
          @PathVariable("id") Long deptId) {

    return Result.success(
            deptService.checkBeforeDelete(deptId));
  }

  /**
   * 删除部门。
   *
   * @param deptId 部门标识
   * @return 删除结果
   */
  @Operation(summary = "删除部门")
  @DeleteMapping("/{id}")
  @RequiresPermission(SecurityPermissionCode.Department.DELETE)
  @YakPermission(
          code = SecurityPermissionCode.Department.DELETE,
          name = "删除部门",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Department.MENU_CODE,
          description = "删除没有下级部门和关联用户的部门")
  public Result<Void> delete(
          @PathVariable("id") Long deptId) {

    deptService.deleteDept(deptId);

    return Result.success(null);
  }

  /**
   * 导入部门树。
   *
   * @param deptDTOList 部门信息列表
   * @return 导入结果
   */
  @Operation(summary = "导入部门树")
  @PostMapping("/import")
  @RequiresPermission(SecurityPermissionCode.Department.IMPORT)
  @YakPermission(
          code = SecurityPermissionCode.Department.IMPORT,
          name = "导入部门",
          group = SecurityPermissionCode.GROUP_NAME,
          groupCode = SecurityPermissionCode.GROUP_CODE,
          menuCode = SecurityPermissionCode.Department.MENU_CODE,
          description = "批量导入部门树")
  public Result<Void> importDept(
          @RequestBody List<DeptDTO> deptDTOList) {

    deptService.saveDept(deptDTOList);

    return Result.success(null);
  }
}
