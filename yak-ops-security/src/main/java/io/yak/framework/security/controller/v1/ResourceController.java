package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.ResourceViewControlDTO;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.enums.resource.ShowLevelCode;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.service.UserResourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资源权限管理接口。
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "资源权限管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/resource")
public class ResourceController {

  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 200;

  private final UserResourceService userResourceService;
  private final ResourceTypeService resourceTypeService;

  public ResourceController(
          UserResourceService userResourceService,
          ResourceTypeService resourceTypeService) {
    this.userResourceService = userResourceService;
    this.resourceTypeService = resourceTypeService;
  }

  @Operation(summary = "查询全部资源类型")
  @GetMapping("/type/list")
  public Result<List<ResourceTypeVO>> typeList() {
    List<ResourceTypeVO> resourceTypes =
            resourceTypeService.getAllResourceTypeList();
    return Result.success(
            resourceTypes == null
                    ? Collections.emptyList()
                    : resourceTypes);
  }

  @Operation(summary = "导入资源类型")
  @PostMapping("/type/import")
  public Result<Void> typeImport(
          @RequestBody(required = false) List<String> typeNameList) {
    List<String> names = typeNameList == null
            ? Collections.emptyList()
            : typeNameList.stream()
            .filter(name -> name != null && !name.trim().isEmpty())
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());

    resourceTypeService.saveResourceType(names);
    return Result.success();
  }

  @Operation(summary = "查询资源查看权限控制状态")
  @GetMapping({"/vpc/status", "/view-control/status"})
  public Result<Boolean> viewControlStatus() {
    return Result.success(
            userResourceService.getViewPermissionControlStatus());
  }

  @Operation(summary = "显式设置资源查看权限控制状态")
  @PutMapping("/view-control/status")
  public Result<Void> setViewControlStatus(
          @RequestBody(required = false)
          ResourceViewControlDTO request) {
    if (request == null || request.getEnabled() == null) {
      return Result.buildParamIllegal("查看权限控制状态不能为空");
    }

    userResourceService.setViewPermissionControlStatus(
            request.getEnabled());
    return Result.success();
  }

  @Operation(summary = "切换资源查看权限控制状态（兼容接口）")
  @PutMapping("/vpc/switch")
  public Result<Void> vpcSwitch() {
    userResourceService.changeResourceViewControlStatus();
    return Result.success();
  }

  @Operation(summary = "查询按用户管理的资源权限数据")
  @PostMapping({"/mbu/list", "/by-user/data"})
  public Result<List<MByUDataVO>> byUserData(
          @RequestBody(required = false)
          MByUDataQueryDTO queryDTO) {
    MByUDataQueryDTO query = queryDTO == null
            ? new MByUDataQueryDTO()
            : queryDTO;
    if (query.getShowLevel() == null) {
      query.setShowLevel(ShowLevelCode.PROJECT.getType());
    }
    if (query.getBatch() == null) {
      query.setBatch(Boolean.FALSE);
    }

    return Result.success(
            userResourceService.getManagerByUserDataList(query));
  }

  @Operation(summary = "查询按资源管理的用户权限数据")
  @PostMapping({"/mbr/list", "/by-resource/data"})
  public Result<List<MByRDataVO>> byResourceData(
          @RequestBody(required = false)
          MByRDataQueryDTO queryDTO) {
    MByRDataQueryDTO query = queryDTO == null
            ? new MByRDataQueryDTO()
            : queryDTO;
    if (query.getBatch() == null) {
      query.setBatch(Boolean.FALSE);
    }

    return Result.success(
            userResourceService.getManagerByResourceDataList(query));
  }

  @Operation(summary = "分页查询按资源管理的权限信息")
  @PostMapping({"/mbr/page", "/by-resource/page"})
  public Result<PagingData<MByRVO>> byResourcePage(
          @RequestBody(required = false)
          MByRQueryDTO queryDTO) {
    MByRQueryDTO query = normalize(queryDTO);
    PagingData<MByRVO> pagingData =
            userResourceService.getManageByResourcePage(query);
    return Result.success(pagingData);
  }

  @Operation(summary = "分页查询按用户管理的权限信息")
  @PostMapping({"/mbu/page", "/by-user/page"})
  public Result<PagingData<MByUVO>> byUserPage(
          @RequestBody(required = false)
          MByUQueryDTO queryDTO) {
    MByUQueryDTO query = normalize(queryDTO);
    PagingData<MByUVO> pagingData =
            userResourceService.getManageByUserPage(query);
    return Result.success(pagingData);
  }

  @Operation(summary = "为单个用户分配资源权限")
  @PutMapping("/by-user/assign")
  public Result<Void> assignByUser(
          @RequestBody AssignToOneUserDTO assignDTO) {
    return assignByUserInternal(assignDTO);
  }

  @Operation(summary = "为单个用户分配资源权限（兼容接口）")
  @PostMapping("/permission/mbu/assign")
  public Result<Void> mbuAssign(
          @RequestBody AssignToOneUserDTO assignDTO) {
    return assignByUserInternal(assignDTO);
  }

  @Operation(summary = "为多个用户分配资源权限")
  @PutMapping("/by-resource/assign")
  public Result<Void> assignByResource(
          @RequestBody AssignToManyUserDTO assignDTO) {
    return assignByResourceInternal(assignDTO);
  }

  @Operation(summary = "为多个用户分配资源权限（兼容接口）")
  @PostMapping("/permission/mbr/assign")
  public Result<Void> mbrAssign(
          @RequestBody AssignToManyUserDTO assignDTO) {
    return assignByResourceInternal(assignDTO);
  }

  @Operation(summary = "批量分配资源权限")
  @PutMapping("/batch/assign")
  public Result<Void> batchAssignStandard(
          @RequestBody BatchAssignDTO assignDTO) {
    return batchAssignInternal(assignDTO);
  }

  @Operation(summary = "批量分配资源权限（兼容接口）")
  @PostMapping("/permission/assign/batch")
  public Result<Void> batchAssign(
          @RequestBody BatchAssignDTO assignDTO) {
    return batchAssignInternal(assignDTO);
  }

  @Operation(summary = "查询资源权限控制级别")
  @PostMapping("/control/level")
  public Result<Integer> getControlLevel(
          @RequestBody ControlLevelQueryDTO queryDTO) {
    ControlLevelCode controlLevel =
            userResourceService.getControlLevel(queryDTO);
    return Result.success(controlLevel.getType());
  }

  private Result<Void> assignByUserInternal(
          AssignToOneUserDTO assignDTO) {
    userResourceService.assignResourcePermission(assignDTO);
    return Result.success();
  }

  private Result<Void> assignByResourceInternal(
          AssignToManyUserDTO assignDTO) {
    userResourceService.assignResourcePermission(assignDTO);
    return Result.success();
  }

  private Result<Void> batchAssignInternal(
          BatchAssignDTO assignDTO) {
    userResourceService.batchAssignResourcePermission(assignDTO);
    return Result.success();
  }

  private MByRQueryDTO normalize(MByRQueryDTO queryDTO) {
    MByRQueryDTO query = queryDTO == null
            ? new MByRQueryDTO()
            : queryDTO;
    normalizePage(query);
    if (query.getShowLevel() == null) {
      query.setShowLevel(ShowLevelCode.PROJECT.getType());
    }
    query.setName(trim(query.getName()));
    return query;
  }

  private MByUQueryDTO normalize(MByUQueryDTO queryDTO) {
    MByUQueryDTO query = queryDTO == null
            ? new MByUQueryDTO()
            : queryDTO;
    normalizePage(query);
    query.setDeptName(trim(query.getDeptName()));
    query.setUserName(trim(query.getUserName()));
    query.setRealName(trim(query.getRealName()));
    return query;
  }

  private void normalizePage(
          io.yak.framework.security.common.dto.PageParamDTO query) {
    query.setPage(Math.max(1, query.getPage()));
    query.setSize(query.getSize() <= 0
            ? DEFAULT_PAGE_SIZE
            : Math.min(query.getSize(), MAX_PAGE_SIZE));
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }
}
