package io.yak.framework.security.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.ResourceDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.entity.UserResource;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.enums.resource.HasLevelCode;
import io.yak.framework.security.common.enums.resource.ShowLevelCode;
import io.yak.framework.security.common.vo.project.ProjectBriefVO;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.dao.UserResourceDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.ResourceExtend;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.service.ProjectService;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.service.UserResourceService;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 用户资源权限服务实现类。
 *
 * <p>按照项目、资源类型和资源三个层级维护用户资源权限，
 * 用于实现资源访问控制和数据隔离。
 *
 * @author weifuwan
 */
@Service("yakSecurityUserResourceServiceImpl")
public class UserResourceServiceImpl
        implements UserResourceService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(
                  UserResourceServiceImpl.class);

  /**
   * 系统级虚拟用户 ID。
   *
   * <p>用于记录全局资源查看权限控制状态。
   */
  private static final Long SYSTEM_USER_ID = 0L;

  private final UserResourceDao userResourceDao;

  private final DeptService deptService;

  private final UserService userService;

  private final ProjectService projectService;

  private final ResourceTypeService resourceTypeService;

  private final ResourceExtend resourceExtend;

  /**
   * 创建用户资源权限服务。
   *
   * @param userResourceDao 用户资源权限数据访问对象
   * @param deptService 部门服务
   * @param userService 用户服务
   * @param projectService 项目服务
   * @param resourceTypeService 资源类型服务
   * @param resourceExtend 资源扩展
   */
  public UserResourceServiceImpl(
          UserResourceDao userResourceDao,
          DeptService deptService,
          UserService userService,
          ProjectService projectService,
          ResourceTypeService resourceTypeService,
          ResourceExtend resourceExtend) {

    this.userResourceDao = userResourceDao;
    this.deptService = deptService;
    this.userService = userService;
    this.projectService = projectService;
    this.resourceTypeService = resourceTypeService;
    this.resourceExtend = resourceExtend;
  }

  /**
   * 根据用户 ID 统计符合条件的资源数量。
   *
   * @param userId 用户 ID
   * @param queryDTO 查询条件
   * @return 资源数量
   */
  @Override
  public int getResourceCntByUserId(
          Long userId,
          UserResourceQueryDTO queryDTO) {

    if (userId == null || queryDTO == null) {
      return 0;
    }

    return userResourceDao.selectCountByUserId(
            userId,
            queryDTO);
  }

  /**
   * 获取资源查看权限控制状态。
   *
   * @return 是否开启查看权限控制
   */
  @Override
  public boolean getViewPermissionControlStatus() {
    UserResourceQueryDTO queryDTO =
            UserResourceQueryDTO
                    .getOpenViewPermissionControlQueryEntity();

    return userResourceDao.selectCountByUserId(
            SYSTEM_USER_ID,
            queryDTO) > 0;
  }

  /**
   * 查询按用户管理的资源权限数据。
   *
   * @param queryDTO 查询条件
   * @return 资源权限数据列表
   */
  @Override
  public List<MByUDataVO> getManagerByUserDataList(
          MByUDataQueryDTO queryDTO) {

    checkParam(queryDTO);

    Long projectId = queryDTO.getProjectId();
    Long resourceTypeId =
            queryDTO.getResourceTypeId();

    int showLevel = queryDTO.getShowLevel();
    int controlLevel =
            queryDTO.getControlLevel();

    Long userId = queryDTO.getUserId();

    boolean batch =
            Boolean.TRUE.equals(
                    queryDTO.getBatch());

    List<MByUDataVO> resultList =
            new ArrayList<>();

    if (Objects.equals(
            ShowLevelCode.PROJECT.getType(),
            showLevel)) {

      buildProjectPermissionData(
              resultList,
              batch,
              controlLevel,
              userId);

      return resultList;
    }

    if (Objects.equals(
            ShowLevelCode.RESOURCE_TYPE.getType(),
            showLevel)) {

      buildResourceTypePermissionData(
              resultList,
              batch,
              controlLevel,
              userId,
              projectId);

      return resultList;
    }

    buildResourcePermissionData(
            resultList,
            batch,
            controlLevel,
            userId,
            projectId,
            resourceTypeId);

    return resultList;
  }

  /**
   * 查询按资源管理的用户权限数据。
   *
   * @param queryDTO 查询条件
   * @return 用户权限数据列表
   */
  @Override
  public List<MByRDataVO>
  getManagerByResourceDataList(
          MByRDataQueryDTO queryDTO) {

    checkParam(queryDTO);

    List<UserBriefVO> userList =
            userService
                    .getAllUserBriefListOrderByCreateTime(
                            false);

    if (CollectionUtils.isEmpty(userList)) {
      return new ArrayList<>();
    }

    boolean batch =
            Boolean.TRUE.equals(
                    queryDTO.getBatch());

    List<MByRDataVO> resultList =
            new ArrayList<>(userList.size());

    /*
     * 不使用 parallelStream。
     *
     * 每个用户都需要访问数据库，并行流容易造成数据库连接池
     * 瞬间被占满，同时返回顺序也不可控。
     */
    for (UserBriefVO user : userList) {
      MByRDataVO dataVO =
              new MByRDataVO();

      dataVO.setUserId(user.getId());
      dataVO.setUserName(
              user.getUserName());
      dataVO.setRealName(
              user.getRealName());

      HasLevelCode hasLevel =
              getHasLevel(
                      batch,
                      queryDTO.getControlLevel(),
                      user.getId(),
                      queryDTO.getProjectId(),
                      queryDTO.getResourceTypeId(),
                      queryDTO.getResourceId());

      dataVO.setHasLevel(
              hasLevel.getType());

      resultList.add(dataVO);
    }

    return resultList;
  }

  /**
   * 切换资源查看权限控制状态。
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void changeResourceViewControlStatus() {
    boolean enabled =
            getViewPermissionControlStatus();

    if (enabled) {
      UserResourceQueryDTO queryDTO =
              UserResourceQueryDTO
                      .getOpenViewPermissionControlQueryEntity();

      userResourceDao.deleteByUserId(
              SYSTEM_USER_ID,
              queryDTO);

      LOGGER.info("关闭资源查看权限控制");
      return;
    }

    userResourceDao.deleteByControlLevel(
            ControlLevelCode.VIEW);

    UserResource controlResource =
            new UserResource();

    controlResource.setUserId(SYSTEM_USER_ID);
    controlResource.setControlLevel(
            ControlLevelCode.VIEW.getType());

    userResourceDao.insert(
            controlResource);

    LOGGER.info("开启资源查看权限控制");
  }

  /**
   * 查询指定用户对资源的权限控制级别。
   *
   * @param queryDTO 查询条件
   * @return 权限控制级别
   */
  @Override
  public ControlLevelCode getControlLevel(
          ControlLevelQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "权限控制级别查询条件不能为空");
    }

    if (queryDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (queryDTO.getProjectId() == null) {
      throw new YakSecurityException(
              ResultCode.PROJECT_ID_CANNOT_BE_NULL);
    }

    if (queryDTO.getResourceTypeId() == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_TYPE_ID_CANNOT_BE_NULL);
    }

    if (queryDTO.getResourceId() == null) {
      throw new YakSecurityException(
              ResultCode.RESOURCE_ID_CANNOT_BE_NULL);
    }

    Integer controlLevel =
            userResourceDao.selectControlLevel(
                    queryDTO);

    if (controlLevel == null) {
      return getViewPermissionControlStatus()
              ? ControlLevelCode.NONE
              : ControlLevelCode.VIEW;
    }

    ControlLevelCode controlLevelCode =
            ControlLevelCode.getByType(
                    controlLevel);

    if (controlLevelCode == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }

    return controlLevelCode;
  }

  /**
   * 为单个用户分配资源权限。
   *
   * @param assignDTO 分配参数
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void assignResourcePermission(
          AssignToOneUserDTO assignDTO) {

    checkParam(assignDTO);

    Long userId = assignDTO.getUserId();
    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId =
            assignDTO.getResourceTypeId();

    int controlLevel =
            assignDTO.getControlLevel();

    UserResourceQueryDTO queryDTO =
            new UserResourceQueryDTO(
                    controlLevel,
                    projectId,
                    resourceTypeId);

    List<Long> excludeIdList =
            normalizeIds(
                    assignDTO.getExcludeIdList());

    if (excludeIdList.isEmpty()) {
      userResourceDao.deleteByUserId(
              userId,
              queryDTO);
    } else if (projectId == null) {
      userResourceDao
              .deleteByUserIdWithoutProjectIdList(
                      userId,
                      queryDTO,
                      excludeIdList);
    } else if (resourceTypeId == null) {
      userResourceDao
              .deleteByUserIdWithoutResourceTypeIdList(
                      userId,
                      queryDTO,
                      excludeIdList);
    }

    List<Long> idList =
            normalizeIds(
                    assignDTO.getIdList());

    if (idList.isEmpty()) {
      return;
    }

    List<UserResource> userResourceList =
            getUserResourceList(
                    projectId,
                    resourceTypeId,
                    controlLevel,
                    idList,
                    Collections.singletonList(
                            userId));

    insertBatch(userResourceList);
  }

  /**
   * 为多个用户分配资源权限。
   *
   * @param assignDTO 分配参数
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void assignResourcePermission(
          AssignToManyUserDTO assignDTO) {

    checkParam(assignDTO);

    List<Long> userIdList =
            normalizeIds(
                    assignDTO.getUserIdList());

    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId =
            assignDTO.getResourceTypeId();
    Long resourceId =
            assignDTO.getResourceId();

    int controlLevel =
            assignDTO.getControlLevel();

    UserResourceQueryDTO queryDTO =
            new UserResourceQueryDTO(
                    controlLevel,
                    projectId,
                    resourceTypeId,
                    resourceId);

    userResourceDao.deleteWithoutUserIdList(
            queryDTO,
            normalizeIds(
                    assignDTO
                            .getExcludeUserIdList()));

    if (userIdList.isEmpty()) {
      return;
    }

    List<ResourceDTO> resourceList =
            new ArrayList<>();

    if (resourceId == null) {
      ResourceExtend resourceExtend =
              this.resourceExtend;

      List<ResourceDTO> extensionResources =
              resourceExtend.getResourceList(
                      projectId,
                      resourceTypeId);

      if (!CollectionUtils.isEmpty(
              extensionResources)) {

        resourceList.addAll(
                extensionResources);
      }
    } else {
      resourceList.add(
              new ResourceDTO(
                      projectId,
                      resourceTypeId,
                      resourceId));
    }

    List<UserResource> userResourceList =
            buildUserResourceList(
                    controlLevel,
                    userIdList,
                    resourceList);

    insertBatch(userResourceList);
  }

  /**
   * 批量分配资源权限。
   *
   * @param assignDTO 批量分配参数
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void batchAssignResourcePermission(
          BatchAssignDTO assignDTO) {

    checkParam(assignDTO);

    List<Long> userIdList =
            normalizeIds(
                    assignDTO.getUserIdList());

    List<Long> idList =
            normalizeIds(
                    assignDTO.getIdList());

    int controlLevel =
            assignDTO.getControlLevel();

    boolean assignFlag =
            Boolean.TRUE.equals(
                    assignDTO.getAssignFlag());

    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId =
            assignDTO.getResourceTypeId();

    deleteOldRelationBeforeBatchAssign(
            projectId,
            resourceTypeId,
            assignFlag,
            controlLevel,
            idList);

    if (idList.isEmpty()
            || userIdList.isEmpty()) {

      return;
    }

    List<UserResource> userResourceList =
            getUserResourceList(
                    projectId,
                    resourceTypeId,
                    controlLevel,
                    idList,
                    userIdList);

    insertBatch(userResourceList);
  }

  /**
   * 分页查询按用户管理的权限信息。
   *
   * @param queryDTO 查询条件
   * @return 按用户管理的权限分页数据
   */
  @Override
  public PagingData<MByUVO> getManageByUserPage(
          MByUQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "用户权限分页查询条件不能为空");
    }

    Map<Long, Dept> deptMap =
            deptService.getAllDeptMap();

    PagingData<UserBriefVO> userPage =
            userService.getUserBriefPage(
                    new UserBriefQueryDTO(
                            queryDTO));

    if (userPage == null
            || CollectionUtils.isEmpty(
            userPage.getBizData())) {

      return new PagingData<>(
              new ArrayList<>(),
              userPage == null
                      ? null
                      : userPage.getPagination());
    }

    boolean viewControlEnabled =
            getViewPermissionControlStatus();

    List<MByUVO> resultList =
            new ArrayList<>(
                    userPage.getBizData().size());

    for (UserBriefVO user : userPage.getBizData()) {
      MByUVO dataVO =
              CopyBeanUtil.copy(
                      user,
                      MByUVO.class);

      if (dataVO == null) {
        continue;
      }

      dataVO.setUserId(user.getId());

      dataVO.setDeptList(
              deptService
                      .getDeptBriefListFromDeptMapByChildId(
                              deptMap,
                              user.getDeptId()));

      dataVO.setAdminResourceCnt(
              userResourceDao
                      .selectCountByUserIdAndControlLevel(
                              user.getId(),
                              ControlLevelCode.ADMIN));

      if (viewControlEnabled) {
        dataVO.setViewResourceCnt(
                userResourceDao
                        .selectCountByUserIdAndControlLevel(
                                user.getId(),
                                ControlLevelCode.VIEW));
      }

      resultList.add(dataVO);
    }

    return new PagingData<>(
            resultList,
            userPage.getPagination());
  }

  /**
   * 分页查询按资源管理的权限信息。
   *
   * @param queryDTO 查询条件
   * @return 按资源管理的权限分页数据
   */
  @Override
  public PagingData<MByRVO> getManageByResourcePage(
          MByRQueryDTO queryDTO) {

    checkParam(queryDTO);

    boolean viewControlEnabled =
            getViewPermissionControlStatus();

    Integer showLevel =
            queryDTO.getShowLevel();

    if (Objects.equals(
            showLevel,
            ShowLevelCode.PROJECT.getType())) {

      return dealProjectLevel(
              queryDTO,
              viewControlEnabled);
    }

    if (Objects.equals(
            showLevel,
            ShowLevelCode.RESOURCE_TYPE.getType())) {

      return dealResourceTypeLevel(
              queryDTO,
              viewControlEnabled);
    }

    return dealResourceLevel(
            queryDTO,
            viewControlEnabled);
  }

  /**
   * 构建项目层级权限数据。
   */
  private void buildProjectPermissionData(
          List<MByUDataVO> resultList,
          boolean batch,
          int controlLevel,
          Long userId) {

    List<ProjectBriefVO> projectList =
            projectService.getProjectBriefList();

    if (CollectionUtils.isEmpty(projectList)) {
      return;
    }

    for (ProjectBriefVO project : projectList) {
      MByUDataVO dataVO =
              new MByUDataVO(
                      project.getId(),
                      project.getProjectName());

      HasLevelCode hasLevel =
              getHasLevel(
                      batch,
                      controlLevel,
                      userId,
                      project.getId(),
                      null,
                      null);

      dataVO.setHasLevel(
              hasLevel.getType());

      resultList.add(dataVO);
    }
  }

  /**
   * 构建资源类型层级权限数据。
   */
  private void buildResourceTypePermissionData(
          List<MByUDataVO> resultList,
          boolean batch,
          int controlLevel,
          Long userId,
          Long projectId) {

    List<ResourceTypeVO> resourceTypeList =
            resourceTypeService
                    .getAllResourceTypeList();

    if (CollectionUtils.isEmpty(
            resourceTypeList)) {

      return;
    }

    for (ResourceTypeVO resourceType
            : resourceTypeList) {

      MByUDataVO dataVO =
              new MByUDataVO(
                      resourceType.getId(),
                      resourceType.getTypeName());

      HasLevelCode hasLevel =
              getHasLevel(
                      batch,
                      controlLevel,
                      userId,
                      projectId,
                      resourceType.getId(),
                      null);

      dataVO.setHasLevel(
              hasLevel.getType());

      resultList.add(dataVO);
    }
  }

  /**
   * 构建资源层级权限数据。
   */
  private void buildResourcePermissionData(
          List<MByUDataVO> resultList,
          boolean batch,
          int controlLevel,
          Long userId,
          Long projectId,
          Long resourceTypeId) {

    ResourceExtend resourceExtend =
            this.resourceExtend;

    List<ResourceDTO> resourceList =
            resourceExtend.getResourceList(
                    projectId,
                    resourceTypeId);

    if (CollectionUtils.isEmpty(resourceList)) {
      return;
    }

    for (ResourceDTO resource : resourceList) {
      MByUDataVO dataVO =
              new MByUDataVO(
                      resource.getResourceId(),
                      resource.getResourceName());

      HasLevelCode hasLevel =
              getHasLevel(
                      batch,
                      controlLevel,
                      userId,
                      projectId,
                      resourceTypeId,
                      resource.getResourceId());

      dataVO.setHasLevel(
              hasLevel.getType());

      resultList.add(dataVO);
    }
  }

  /**
   * 计算当前节点的授权状态。
   */
  private HasLevelCode getHasLevel(
          boolean batch,
          int controlLevel,
          Long userId,
          Long projectId,
          Long resourceTypeId,
          Long resourceId) {

    if (batch) {
      return HasLevelCode.NONE;
    }

    UserResourceQueryDTO queryDTO =
            new UserResourceQueryDTO(
                    controlLevel,
                    projectId,
                    resourceTypeId,
                    resourceId);

    int assignedResourceCount =
            getResourceCntByUserId(
                    userId,
                    queryDTO);

    if (assignedResourceCount <= 0) {
      return HasLevelCode.NONE;
    }

    if (resourceId != null) {
      return HasLevelCode.ALL;
    }

    int totalResourceCount =
            resourceExtend.getResourceCnt(
                    projectId,
                    resourceTypeId);

    if (totalResourceCount <= 0) {
      return HasLevelCode.NONE;
    }

    return assignedResourceCount >= totalResourceCount
            ? HasLevelCode.ALL
            : HasLevelCode.HALF;
  }

  /**
   * 构建待保存的用户资源权限关系。
   */
  private List<UserResource> getUserResourceList(
          Long projectId,
          Long resourceTypeId,
          int controlLevel,
          List<Long> idList,
          List<Long> userIdList) {

    List<Long> validIds =
            normalizeIds(idList);

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validIds.isEmpty()
            || validUserIds.isEmpty()) {

      return new ArrayList<>();
    }

    List<Long> projectIdList;
    List<Long> resourceTypeIdList;
    List<Long> resourceIdList = null;

    if (projectId == null) {
      projectIdList =
              new ArrayList<>(validIds);

      resourceTypeIdList =
              normalizeIds(
                      resourceTypeService
                              .getAllResourceTypeIdList());
    } else if (resourceTypeId == null) {
      projectIdList =
              Collections.singletonList(
                      projectId);

      resourceTypeIdList =
              new ArrayList<>(validIds);
    } else {
      projectIdList =
              Collections.singletonList(
                      projectId);

      resourceTypeIdList =
              Collections.singletonList(
                      resourceTypeId);

      resourceIdList =
              new ArrayList<>(validIds);
    }

    List<ResourceDTO> resourceList =
            getResourceDTOList(
                    projectIdList,
                    resourceTypeIdList,
                    resourceIdList);

    return buildUserResourceList(
            controlLevel,
            validUserIds,
            resourceList);
  }

  /**
   * 查询或构建资源列表。
   */
  private List<ResourceDTO> getResourceDTOList(
          List<Long> projectIdList,
          List<Long> resourceTypeIdList,
          List<Long> resourceIdList) {

    if (CollectionUtils.isEmpty(projectIdList)
            || CollectionUtils.isEmpty(
            resourceTypeIdList)) {

      return new ArrayList<>();
    }

    List<ResourceDTO> resourceList =
            new ArrayList<>();

    ResourceExtend resourceExtend =
            this.resourceExtend;

    for (Long projectId : projectIdList) {
      if (projectId == null) {
        continue;
      }

      for (Long resourceTypeId
              : resourceTypeIdList) {

        if (resourceTypeId == null) {
          continue;
        }

        if (resourceIdList == null) {
          List<ResourceDTO> extensionResources =
                  resourceExtend.getResourceList(
                          projectId,
                          resourceTypeId);

          if (!CollectionUtils.isEmpty(
                  extensionResources)) {

            resourceList.addAll(
                    extensionResources);
          }

          continue;
        }

        for (Long resourceId : resourceIdList) {
          if (resourceId == null) {
            continue;
          }

          resourceList.add(
                  new ResourceDTO(
                          projectId,
                          resourceTypeId,
                          resourceId));
        }
      }
    }

    return resourceList;
  }

  /**
   * 构建用户资源权限关系列表。
   */
  private List<UserResource> buildUserResourceList(
          int controlLevel,
          List<Long> userIdList,
          List<ResourceDTO> resourceList) {

    if (CollectionUtils.isEmpty(userIdList)
            || CollectionUtils.isEmpty(
            resourceList)) {

      return new ArrayList<>();
    }

    List<UserResource> userResourceList =
            new ArrayList<>(
                    userIdList.size()
                            * resourceList.size());

    for (Long userId : userIdList) {
      if (userId == null) {
        continue;
      }

      for (ResourceDTO resource : resourceList) {
        if (resource == null) {
          continue;
        }

        UserResource userResource =
                new UserResource();

        userResource.setUserId(userId);
        userResource.setControlLevel(
                controlLevel);

        userResourceList.add(userResource);
      }
    }

    return userResourceList;
  }

  /**
   * 删除批量分配前的旧权限关系。
   */
  private void deleteOldRelationBeforeBatchAssign(
          Long projectId,
          Long resourceTypeId,
          boolean assignFlag,
          int controlLevel,
          List<Long> idList) {

    if (CollectionUtils.isEmpty(idList)) {
      return;
    }

    UserResourceQueryDTO queryDTO =
            new UserResourceQueryDTO(
                    controlLevel,
                    projectId,
                    resourceTypeId);

    if (!assignFlag) {
      userResourceDao.deleteByUserIdList(
              idList,
              queryDTO);

      return;
    }

    if (projectId == null) {
      userResourceDao.deleteByProjectIdList(
              idList,
              queryDTO);
    } else if (resourceTypeId == null) {
      userResourceDao
              .deleteByResourceTypeIdList(
                      idList,
                      queryDTO);
    } else {
      userResourceDao.deleteByResourceIdList(
              idList,
              queryDTO);
    }
  }

  /**
   * 查询具有完整权限的用户数量。
   */
  private int getAdminOrViewUserCnt(
          UserResourceQueryDTO queryDTO) {

    List<Long> userIdList =
            userResourceDao
                    .selectUserIdListGroupByUserId(
                            queryDTO);

    if (CollectionUtils.isEmpty(userIdList)) {
      return 0;
    }

    int totalResourceCount =
            resourceExtend.getResourceCnt(
                    queryDTO.getProjectId(),
                    queryDTO.getResourceTypeId());

    if (totalResourceCount <= 0) {
      return 0;
    }

    int result = 0;

    for (Long userId : userIdList) {
      int assignedResourceCount =
              userResourceDao.selectCountByUserId(
                      userId,
                      queryDTO);

      if (assignedResourceCount
              >= totalResourceCount) {

        result++;
      }
    }

    return result;
  }

  /**
   * 处理项目层级分页数据。
   */
  private PagingData<MByRVO> dealProjectLevel(
          MByRQueryDTO queryDTO,
          boolean viewControlEnabled) {

    PagingData<ProjectBriefVO> projectPage =
            projectService.getProjectBriefPage(
                    new ProjectBriefQueryDTO(
                            queryDTO));

    if (projectPage == null
            || CollectionUtils.isEmpty(
            projectPage.getBizData())) {

      return new PagingData<>(
              new ArrayList<>(),
              projectPage == null
                      ? null
                      : projectPage.getPagination());
    }

    List<MByRVO> resultList =
            new ArrayList<>(
                    projectPage.getBizData().size());

    for (ProjectBriefVO project
            : projectPage.getBizData()) {

      MByRVO dataVO = new MByRVO();

      dataVO.setProjectId(
              project.getId());
      dataVO.setProjectCode(
              project.getProjectCode());
      dataVO.setProjectName(
              project.getProjectName());

      UserResourceQueryDTO adminQuery =
              new UserResourceQueryDTO(
                      ControlLevelCode.ADMIN.getType(),
                      project.getId());

      dataVO.setAdminUserCnt(
              getAdminOrViewUserCnt(
                      adminQuery));

      if (viewControlEnabled) {
        UserResourceQueryDTO viewQuery =
                new UserResourceQueryDTO(
                        ControlLevelCode.VIEW.getType(),
                        project.getId());

        dataVO.setViewUserCnt(
                getAdminOrViewUserCnt(
                        viewQuery));
      }

      resultList.add(dataVO);
    }

    return new PagingData<>(
            resultList,
            projectPage.getPagination());
  }

  /**
   * 处理资源类型层级分页数据。
   */
  private PagingData<MByRVO> dealResourceTypeLevel(
          MByRQueryDTO queryDTO,
          boolean viewControlEnabled) {

    PagingData<ResourceTypeVO> resourceTypePage =
            resourceTypeService
                    .getResourceTypePage(
                            new ResourceTypeQueryDTO(
                                    queryDTO));

    if (resourceTypePage == null
            || CollectionUtils.isEmpty(
            resourceTypePage.getBizData())) {

      return new PagingData<>(
              new ArrayList<>(),
              resourceTypePage == null
                      ? null
                      : resourceTypePage
                      .getPagination());
    }

    ProjectBriefVO project =
            projectService
                    .getProjectBriefByProjectId(
                            queryDTO.getProjectId());

    List<MByRVO> resultList =
            new ArrayList<>(
                    resourceTypePage
                            .getBizData()
                            .size());

    for (ResourceTypeVO resourceType
            : resourceTypePage.getBizData()) {

      MByRVO dataVO = new MByRVO();

      dataVO.setProjectId(
              queryDTO.getProjectId());

      if (project != null) {
        dataVO.setProjectName(
                project.getProjectName());
      }

      dataVO.setResourceTypeId(
              resourceType.getId());
      dataVO.setResourceTypeName(
              resourceType.getTypeName());

      UserResourceQueryDTO adminQuery =
              new UserResourceQueryDTO(
                      ControlLevelCode.ADMIN.getType(),
                      queryDTO.getProjectId(),
                      resourceType.getId());

      dataVO.setAdminUserCnt(
              getAdminOrViewUserCnt(
                      adminQuery));

      if (viewControlEnabled) {
        UserResourceQueryDTO viewQuery =
                new UserResourceQueryDTO(
                        ControlLevelCode.VIEW.getType(),
                        queryDTO.getProjectId(),
                        resourceType.getId());

        dataVO.setViewUserCnt(
                getAdminOrViewUserCnt(
                        viewQuery));
      }

      resultList.add(dataVO);
    }

    return new PagingData<>(
            resultList,
            resourceTypePage.getPagination());
  }

  /**
   * 处理资源层级分页数据。
   */
  private PagingData<MByRVO> dealResourceLevel(
          MByRQueryDTO queryDTO,
          boolean viewControlEnabled) {

    PagingData<ResourceDTO> resourcePage =
            resourceExtend.getResourcePage(
                    queryDTO.getProjectId(),
                    queryDTO.getResourceTypeId(),
                    queryDTO.getName(),
                    queryDTO.getPage(),
                    queryDTO.getSize());

    if (resourcePage == null) {
      return new PagingData<>();
    }

    ResourceTypeVO resourceType =
            resourceTypeService
                    .getResourceTypeByResourceTypeId(
                            queryDTO.getResourceTypeId());

    List<MByRVO> resultList =
            new ArrayList<>();

    if (CollectionUtils.isEmpty(
            resourcePage.getBizData())) {

      return new PagingData<>(
              resultList,
              resourcePage.getPagination());
    }

    for (ResourceDTO resource
            : resourcePage.getBizData()) {

      MByRVO dataVO = new MByRVO();

      dataVO.setProjectId(
              queryDTO.getProjectId());
      dataVO.setResourceId(
              resource.getResourceId());
      dataVO.setResourceName(
              resource.getResourceName());

      if (resourceType != null) {
        dataVO.setResourceTypeId(
                resourceType.getId());
        dataVO.setResourceTypeName(
                resourceType.getTypeName());
      }

      UserResourceQueryDTO adminQuery =
              new UserResourceQueryDTO(
                      ControlLevelCode.ADMIN.getType(),
                      queryDTO.getProjectId(),
                      queryDTO.getResourceTypeId(),
                      resource.getResourceId());

      dataVO.setAdminUserCnt(
              userResourceDao
                      .selectCountGroupByUserId(
                              adminQuery));

      if (viewControlEnabled) {
        UserResourceQueryDTO viewQuery =
                new UserResourceQueryDTO(
                        ControlLevelCode.VIEW.getType(),
                        queryDTO.getProjectId(),
                        queryDTO.getResourceTypeId(),
                        resource.getResourceId());

        dataVO.setViewUserCnt(
                userResourceDao
                        .selectCountGroupByUserId(
                                viewQuery));
      }

      resultList.add(dataVO);
    }

    return new PagingData<>(
            resultList,
            resourcePage.getPagination());
  }

  /**
   * 校验资源控制层级参数。
   */
  private void checkParam(
          Integer controlLevel,
          Long projectId,
          Long resourceTypeId,
          Long resourceId) {

    if (projectId == null) {
      throw new YakSecurityException(
              ResultCode.PROJECT_ID_CANNOT_BE_NULL);
    }

    if (resourceTypeId == null
            && resourceId != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR);
    }

    if (controlLevel == null
            || ControlLevelCode.getByType(
            controlLevel) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }
  }

  /**
   * 校验按资源查询参数。
   */
  private void checkParam(
          MByRDataQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "按资源查询条件不能为空");
    }

    checkParam(
            queryDTO.getControlLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId(),
            queryDTO.getResourceId());
  }

  /**
   * 校验按用户查询参数。
   */
  private void checkParam(
          MByUDataQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "按用户查询条件不能为空");
    }

    if (queryDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (ControlLevelCode.getByType(
            queryDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }

    checkParam(
            queryDTO.getShowLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId());
  }

  /**
   * 校验单用户分配参数。
   */
  private void checkParam(
          AssignToOneUserDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "单用户资源分配参数不能为空");
    }

    if (assignDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (ControlLevelCode.getByType(
            assignDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }

    if (assignDTO.getProjectId() == null
            && assignDTO.getResourceTypeId()
            != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR_2);
    }
  }

  /**
   * 校验多用户分配参数。
   */
  private void checkParam(
          AssignToManyUserDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "多用户资源分配参数不能为空");
    }

    checkParam(
            assignDTO.getControlLevel(),
            assignDTO.getProjectId(),
            assignDTO.getResourceTypeId(),
            assignDTO.getResourceId());
  }

  /**
   * 校验批量分配参数。
   */
  private void checkParam(
          BatchAssignDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "批量资源分配参数不能为空");
    }

    if (assignDTO.getUserIdList() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    if (assignDTO.getAssignFlag() == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_ASSIGN_BATCH_FLAG_CANNOT_BE_NULL);
    }

    if (assignDTO.getProjectId() == null
            && assignDTO.getResourceTypeId()
            != null) {

      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR_2);
    }

    if (ControlLevelCode.getByType(
            assignDTO.getControlLevel()) == null) {

      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_CONTROL_LEVEL);
    }
  }

  /**
   * 校验按资源分页查询参数。
   */
  private void checkParam(
          MByRQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "资源权限分页查询条件不能为空");
    }

    checkParam(
            queryDTO.getShowLevel(),
            queryDTO.getProjectId(),
            queryDTO.getResourceTypeId());
  }

  /**
   * 校验资源展示层级。
   */
  private void checkParam(
          Integer showLevel,
          Long projectId,
          Long resourceTypeId) {

    ShowLevelCode showLevelCode =
            ShowLevelCode.getByType(
                    showLevel);

    if (showLevelCode == null) {
      throw new YakSecurityException(
              ResultCode
                      .RESOURCE_INVALID_SHOW_LEVEL);
    }

    if (showLevel
            >= ShowLevelCode
            .RESOURCE_TYPE
            .getType()) {

      if (projectId == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_SHOW_LEVEL_ERROR);
      }

      ProjectBriefVO project =
              projectService
                      .getProjectBriefByProjectId(
                              projectId);

      if (project == null) {
        throw new YakSecurityException(
                ResultCode.PROJECT_NOT_EXISTS);
      }
    }

    if (showLevel
            >= ShowLevelCode
            .RESOURCE
            .getType()) {

      if (resourceTypeId == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_SHOW_LEVEL_ERROR_2);
      }

      ResourceTypeVO resourceType =
              resourceTypeService
                      .getResourceTypeByResourceTypeId(
                              resourceTypeId);

      if (resourceType == null) {
        throw new YakSecurityException(
                ResultCode
                        .RESOURCE_TYPE_NOT_EXISTS);
      }
    }
  }

  /**
   * 批量保存用户资源权限关系。
   */
  private void insertBatch(
          List<UserResource> userResourceList) {

    if (CollectionUtils.isEmpty(
            userResourceList)) {

      return;
    }

    userResourceDao.insertBatch(
            userResourceList);
  }

  /**
   * 过滤空 ID 并去重。
   */
  private List<Long> normalizeIds(
          List<Long> idList) {

    if (CollectionUtils.isEmpty(idList)) {
      return new ArrayList<>();
    }

    return idList.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }
}
