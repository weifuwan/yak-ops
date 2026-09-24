package io.yak.ops.security.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.ops.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.ops.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.ops.security.common.dto.resource.BatchAssignDTO;
import io.yak.ops.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.ops.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.ops.security.common.dto.resource.MByRQueryDTO;
import io.yak.ops.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.ops.security.common.dto.resource.MByUQueryDTO;
import io.yak.ops.security.common.dto.resource.ResourceDTO;
import io.yak.ops.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.ops.security.common.entity.UserResource;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.enums.resource.ControlLevelCode;
import io.yak.ops.security.common.vo.resource.MByRDataVO;
import io.yak.ops.security.common.vo.resource.MByRVO;
import io.yak.ops.security.common.vo.resource.MByUDataVO;
import io.yak.ops.security.common.vo.resource.MByUVO;
import io.yak.ops.security.dao.UserResourceDao;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.extend.ResourceExtend;
import io.yak.ops.security.service.ResourceTypeService;
import io.yak.ops.security.service.UserResourceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源授权服务修正层。
 *
 * <p>查询能力继续复用原有实现，授权写入在此处统一补齐项目、资源类型和
 * 资源标识，避免只保存用户和控制级别而产生无法命中的权限记录。</p>
 */
@Service("yakSecurityResourceAuthorizationService")
@Primary
public class ResourceAuthorizationService implements UserResourceService {

  private static final Long SYSTEM_USER_ID = 0L;
  private static final Long SYSTEM_SCOPE_ID = 0L;

  private final UserResourceService delegate;
  private final UserResourceDao userResourceDao;
  private final ResourceTypeService resourceTypeService;
  private final ResourceExtend resourceExtend;

  public ResourceAuthorizationService(
          @Qualifier("yakSecurityUserResourceServiceImpl")
          UserResourceService delegate,
          UserResourceDao userResourceDao,
          ResourceTypeService resourceTypeService,
          ResourceExtend resourceExtend) {
    this.delegate = delegate;
    this.userResourceDao = userResourceDao;
    this.resourceTypeService = resourceTypeService;
    this.resourceExtend = resourceExtend;
  }

  @Override
  public int getResourceCntByUserId(
          Long userId,
          UserResourceQueryDTO queryDTO) {
    return delegate.getResourceCntByUserId(userId, queryDTO);
  }

  @Override
  public PagingData<MByRVO> getManageByResourcePage(
          MByRQueryDTO queryDTO) {
    return delegate.getManageByResourcePage(queryDTO);
  }

  @Override
  public PagingData<MByUVO> getManageByUserPage(
          MByUQueryDTO queryDTO) {
    return delegate.getManageByUserPage(queryDTO);
  }

  @Override
  public List<MByUDataVO> getManagerByUserDataList(
          MByUDataQueryDTO queryDTO) {
    return delegate.getManagerByUserDataList(queryDTO);
  }

  @Override
  public List<MByRDataVO> getManagerByResourceDataList(
          MByRDataQueryDTO queryDTO) {
    return delegate.getManagerByResourceDataList(queryDTO);
  }

  @Override
  public ControlLevelCode getControlLevel(
          ControlLevelQueryDTO queryDTO) {
    return delegate.getControlLevel(queryDTO);
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void assignResourcePermission(
          AssignToOneUserDTO assignDTO) {
    validate(assignDTO);

    Long userId = assignDTO.getUserId();
    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId = assignDTO.getResourceTypeId();
    int controlLevel = assignDTO.getControlLevel();

    UserResourceQueryDTO scope = new UserResourceQueryDTO(
            controlLevel,
            projectId,
            resourceTypeId);

    List<Long> partialIds = normalizeIds(
            assignDTO.getExcludeIdList());

    if (partialIds.isEmpty()) {
      userResourceDao.deleteByUserId(userId, scope);
    } else if (projectId == null) {
      userResourceDao.deleteByUserIdWithoutProjectIdList(
              userId,
              scope,
              partialIds);
    } else if (resourceTypeId == null) {
      userResourceDao.deleteByUserIdWithoutResourceTypeIdList(
              userId,
              scope,
              partialIds);
    }

    List<ResourceDTO> resources = resolveResourcesForIds(
            projectId,
            resourceTypeId,
            normalizeIds(assignDTO.getIdList()));

    insertRelations(
            Collections.singletonList(userId),
            resources,
            controlLevel);
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void assignResourcePermission(
          AssignToManyUserDTO assignDTO) {
    validate(assignDTO);

    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId = assignDTO.getResourceTypeId();
    Long resourceId = assignDTO.getResourceId();
    int controlLevel = assignDTO.getControlLevel();

    UserResourceQueryDTO scope = new UserResourceQueryDTO(
            controlLevel,
            projectId,
            resourceTypeId,
            resourceId);

    userResourceDao.deleteWithoutUserIdList(
            scope,
            normalizeIds(assignDTO.getExcludeUserIdList()));

    List<Long> userIds = normalizeIds(
            assignDTO.getUserIdList());
    if (userIds.isEmpty()) {
      return;
    }

    insertRelations(
            userIds,
            resolveScopeResources(
                    projectId,
                    resourceTypeId,
                    resourceId),
            controlLevel);
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void batchAssignResourcePermission(
          BatchAssignDTO assignDTO) {
    validate(assignDTO);

    List<Long> userIds = normalizeIds(
            assignDTO.getUserIdList());
    List<Long> ids = normalizeIds(assignDTO.getIdList());
    int controlLevel = assignDTO.getControlLevel();
    Long projectId = assignDTO.getProjectId();
    Long resourceTypeId = assignDTO.getResourceTypeId();

    UserResourceQueryDTO scope = new UserResourceQueryDTO(
            controlLevel,
            projectId,
            resourceTypeId);

    if (Boolean.TRUE.equals(assignDTO.getAssignFlag())) {
      deleteByScopeIds(
              projectId,
              resourceTypeId,
              ids,
              scope);
    } else {
      // 按用户批量授权时，应清理 userIdList，而不是误把资源 idList 当作用户 ID。
      userResourceDao.deleteByUserIdList(userIds, scope);
    }

    if (userIds.isEmpty() || ids.isEmpty()) {
      return;
    }

    insertRelations(
            userIds,
            resolveResourcesForIds(
                    projectId,
                    resourceTypeId,
                    ids),
            controlLevel);
  }

  @Override
  public boolean getViewPermissionControlStatus() {
    return userResourceDao.selectCountByUserId(
            SYSTEM_USER_ID,
            viewControlQuery()) > 0;
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void setViewPermissionControlStatus(boolean enabled) {
    boolean current = getViewPermissionControlStatus();
    if (current == enabled) {
      return;
    }

    if (!enabled) {
      userResourceDao.deleteByUserId(
              SYSTEM_USER_ID,
              viewControlQuery());
      return;
    }

    // 首次启用查看权限控制时从空授权开始，避免历史遗留查看记录直接生效。
    userResourceDao.deleteByControlLevel(ControlLevelCode.VIEW);

    UserResource marker = new UserResource();
    marker.setUserId(SYSTEM_USER_ID);
    marker.setProjectId(SYSTEM_SCOPE_ID);
    marker.setResourceTypeId(SYSTEM_SCOPE_ID);
    marker.setResourceId(SYSTEM_SCOPE_ID);
    marker.setControlLevel(ControlLevelCode.NONE.getType());
    userResourceDao.insert(marker);
  }

  @Override
  public void changeResourceViewControlStatus() {
    setViewPermissionControlStatus(
            !getViewPermissionControlStatus());
  }

  private UserResourceQueryDTO viewControlQuery() {
    return UserResourceQueryDTO
            .getOpenViewPermissionControlQueryEntity();
  }

  private void deleteByScopeIds(
          Long projectId,
          Long resourceTypeId,
          List<Long> ids,
          UserResourceQueryDTO scope) {
    if (ids.isEmpty()) {
      return;
    }

    if (projectId == null) {
      userResourceDao.deleteByProjectIdList(ids, scope);
    } else if (resourceTypeId == null) {
      userResourceDao.deleteByResourceTypeIdList(ids, scope);
    } else {
      userResourceDao.deleteByResourceIdList(ids, scope);
    }
  }

  private List<ResourceDTO> resolveResourcesForIds(
          Long projectId,
          Long resourceTypeId,
          List<Long> ids) {
    if (ids.isEmpty()) {
      return new ArrayList<>();
    }

    List<ResourceDTO> resources = new ArrayList<>();

    if (projectId == null) {
      List<Long> typeIds = normalizeIds(
              resourceTypeService.getAllResourceTypeIdList());
      for (Long selectedProjectId : ids) {
        for (Long typeId : typeIds) {
          resources.addAll(
                  resourcesForType(
                          selectedProjectId,
                          typeId));
        }
      }
      return distinctResources(resources);
    }

    if (resourceTypeId == null) {
      for (Long selectedTypeId : ids) {
        resources.addAll(
                resourcesForType(
                        projectId,
                        selectedTypeId));
      }
      return distinctResources(resources);
    }

    Set<Long> selectedResourceIds =
            new LinkedHashSet<>(ids);
    for (ResourceDTO resource : resourcesForType(
            projectId,
            resourceTypeId)) {
      if (selectedResourceIds.contains(
              resource.getResourceId())) {
        resources.add(resource);
      }
    }

    return distinctResources(resources);
  }

  private List<ResourceDTO> resolveScopeResources(
          Long projectId,
          Long resourceTypeId,
          Long resourceId) {
    if (resourceId != null) {
      List<ResourceDTO> matches = resourcesForType(
              projectId,
              resourceTypeId).stream()
              .filter(resource -> Objects.equals(
                      resource.getResourceId(),
                      resourceId))
              .collect(Collectors.toList());

      if (matches.isEmpty()) {
        throw new IllegalArgumentException(
                "资源不存在或不属于当前项目和资源类型");
      }
      return matches;
    }

    if (resourceTypeId != null) {
      return resourcesForType(projectId, resourceTypeId);
    }

    List<ResourceDTO> resources = new ArrayList<>();
    for (Long typeId : normalizeIds(
            resourceTypeService.getAllResourceTypeIdList())) {
      resources.addAll(resourcesForType(projectId, typeId));
    }
    return distinctResources(resources);
  }

  private List<ResourceDTO> resourcesForType(
          Long projectId,
          Long resourceTypeId) {
    if (projectId == null || resourceTypeId == null) {
      return new ArrayList<>();
    }

    List<ResourceDTO> source = resourceExtend.getResourceList(
            projectId,
            resourceTypeId);
    if (CollectionUtils.isEmpty(source)) {
      return new ArrayList<>();
    }

    List<ResourceDTO> result = new ArrayList<>();
    for (ResourceDTO resource : source) {
      if (resource == null || resource.getResourceId() == null) {
        continue;
      }

      ResourceDTO normalized = new ResourceDTO();
      normalized.setProjectId(
              resource.getProjectId() == null
                      ? projectId
                      : resource.getProjectId());
      normalized.setResourceTypeId(
              resource.getResourceTypeId() == null
                      ? resourceTypeId
                      : resource.getResourceTypeId());
      normalized.setResourceId(resource.getResourceId());
      normalized.setResourceName(resource.getResourceName());
      result.add(normalized);
    }

    return distinctResources(result);
  }

  private List<ResourceDTO> distinctResources(
          List<ResourceDTO> resources) {
    Map<String, ResourceDTO> distinct = new LinkedHashMap<>();
    if (resources == null) {
      return new ArrayList<>();
    }

    for (ResourceDTO resource : resources) {
      if (resource == null
              || resource.getProjectId() == null
              || resource.getResourceTypeId() == null
              || resource.getResourceId() == null) {
        continue;
      }

      String key = resource.getProjectId()
              + ":" + resource.getResourceTypeId()
              + ":" + resource.getResourceId();
      distinct.putIfAbsent(key, resource);
    }

    return new ArrayList<>(distinct.values());
  }

  private void insertRelations(
          List<Long> userIds,
          List<ResourceDTO> resources,
          int controlLevel) {
    List<Long> normalizedUserIds = normalizeIds(userIds);
    List<ResourceDTO> normalizedResources =
            distinctResources(resources);

    if (normalizedUserIds.isEmpty()
            || normalizedResources.isEmpty()) {
      return;
    }

    Map<String, UserResource> relations = new LinkedHashMap<>();
    for (Long userId : normalizedUserIds) {
      for (ResourceDTO resource : normalizedResources) {
        UserResource relation = new UserResource();
        relation.setUserId(userId);
        relation.setProjectId(resource.getProjectId());
        relation.setResourceTypeId(
                resource.getResourceTypeId());
        relation.setResourceId(resource.getResourceId());
        relation.setControlLevel(controlLevel);

        String key = userId
                + ":" + resource.getProjectId()
                + ":" + resource.getResourceTypeId()
                + ":" + resource.getResourceId()
                + ":" + controlLevel;
        relations.putIfAbsent(key, relation);
      }
    }

    userResourceDao.insertBatch(
            new ArrayList<>(relations.values()));
  }

  private void validate(AssignToOneUserDTO assignDTO) {
    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "单用户资源分配参数不能为空");
    }
    if (assignDTO.getUserId() == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }
    validateHierarchy(
            assignDTO.getProjectId(),
            assignDTO.getResourceTypeId(),
            null);
    validateControlLevel(assignDTO.getControlLevel());
  }

  private void validate(AssignToManyUserDTO assignDTO) {
    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "多用户资源分配参数不能为空");
    }
    if (assignDTO.getProjectId() == null) {
      throw new YakSecurityException(
              ResultCode.PROJECT_ID_CANNOT_BE_NULL);
    }
    validateHierarchy(
            assignDTO.getProjectId(),
            assignDTO.getResourceTypeId(),
            assignDTO.getResourceId());
    validateControlLevel(assignDTO.getControlLevel());
  }

  private void validate(BatchAssignDTO assignDTO) {
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
    validateHierarchy(
            assignDTO.getProjectId(),
            assignDTO.getResourceTypeId(),
            null);
    validateControlLevel(assignDTO.getControlLevel());
  }

  private void validateHierarchy(
          Long projectId,
          Long resourceTypeId,
          Long resourceId) {
    if (resourceTypeId != null && projectId == null) {
      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR_2);
    }
    if (resourceId != null && resourceTypeId == null) {
      throw new YakSecurityException(
              ResultCode.RESOURCE_ASSIGN_ERROR);
    }
  }

  private void validateControlLevel(Integer controlLevel) {
    if (!Objects.equals(
            controlLevel,
            ControlLevelCode.VIEW.getType())
            && !Objects.equals(
            controlLevel,
            ControlLevelCode.ADMIN.getType())) {
      throw new YakSecurityException(
              ResultCode.RESOURCE_INVALID_CONTROL_LEVEL);
    }
  }

  private List<Long> normalizeIds(List<Long> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return new ArrayList<>();
    }

    return ids.stream()
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .distinct()
            .collect(Collectors.toList());
  }
}
