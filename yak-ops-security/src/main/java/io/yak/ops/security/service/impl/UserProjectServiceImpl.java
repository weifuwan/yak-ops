package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.dto.user.UserProjectDTO;
import io.yak.ops.security.common.entity.UserProject;
import io.yak.ops.security.common.enums.project.ProjectUserCode;
import io.yak.ops.security.dao.UserProjectDao;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.service.UserProjectService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 用户项目关系服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityUserProjectServiceImpl")
public class UserProjectServiceImpl
        implements UserProjectService {

  /**
   * 普通项目用户类型。
   */
  private static final int NORMAL_USER_TYPE = 0;

  /**
   * 项目负责人类型。
   */
  private static final int OWNER_USER_TYPE = 1;

  private final UserProjectDao userProjectDao;

  private final PermissionCache permissionCache;

  /**
   * 创建用户项目关系服务。
   *
   * @param userProjectDao 用户项目关系数据访问对象
   * @param permissionCache 用户授权缓存
   */
  public UserProjectServiceImpl(
          UserProjectDao userProjectDao,
          PermissionCache permissionCache) {

    this.userProjectDao = userProjectDao;
    this.permissionCache = permissionCache;
  }

  /**
   * 根据项目 ID 和用户类型查询用户 ID。
   *
   * @param projectId 项目 ID
   * @param projectUserCode 项目用户类型
   * @return 用户 ID 列表
   */
  @Override
  public List<Long> getUserIdListByProjectId(
          Long projectId,
          ProjectUserCode projectUserCode) {

    if (projectId == null
            || projectUserCode == null) {

      return new ArrayList<>();
    }

    List<Long> userIdList =
            userProjectDao
                    .selectUserIdListByProjectId(
                            projectId,
                            projectUserCode.getType());

    return userIdList == null
            ? new ArrayList<>()
            : userIdList;
  }

  /**
   * 根据用户 ID 集合查询项目 ID。
   *
   * @param userIdList 用户 ID 列表
   * @return 项目 ID 列表
   */
  @Override
  public List<Long> getProjectIdListByUserIdList(
          List<Long> userIdList) {

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<Long> projectIdList =
            userProjectDao
                    .selectProjectIdListByUserIdList(
                            validUserIds);

    return projectIdList == null
            ? new ArrayList<>()
            : projectIdList;
  }

  /**
   * 保存普通用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveUserProject(
          Long projectId,
          List<Long> userIdList) {

    saveProjectRelation(
            projectId,
            userIdList,
            NORMAL_USER_TYPE);
  }

  /**
   * 删除普通用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void delUserProject(
          Long projectId,
          List<Long> userIdList) {

    deleteProjectRelation(
            projectId,
            userIdList,
            NORMAL_USER_TYPE);
  }

  /**
   * 保存项目负责人关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveOwnerProject(
          Long projectId,
          List<Long> ownerIdList) {

    saveProjectRelation(
            projectId,
            ownerIdList,
            OWNER_USER_TYPE);
  }

  /**
   * 删除项目负责人关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void delOwnerProject(
          Long projectId,
          List<Long> ownerIdList) {

    deleteProjectRelation(
            projectId,
            ownerIdList,
            OWNER_USER_TYPE);
  }

  /**
   * 全量更新普通用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateUserProject(
          Long projectId,
          List<Long> userIdList) {

    if (projectId == null) {
      return;
    }

    deleteUserProjectByProjectId(projectId);
    saveUserProject(projectId, userIdList);
  }

  /**
   * 增量补充项目关联的普通用户。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateUserInformationAssociatedWithProject(
          Long projectId,
          List<Long> userIdList) {

    appendMissingProjectRelations(
            projectId,
            userIdList,
            NORMAL_USER_TYPE);
  }

  /**
   * 全量更新项目负责人关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateOwnerProject(
          Long projectId,
          List<Long> ownerIdList) {

    if (projectId == null) {
      return;
    }

    deleteOwnerProjectByProjectId(projectId);
    saveOwnerProject(projectId, ownerIdList);
  }

  /**
   * 增量补充项目关联的负责人。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateOwnerInformationAssociatedWithProject(
          Long projectId,
          List<Long> ownerIdList) {

    appendMissingProjectRelations(
            projectId,
            ownerIdList,
            OWNER_USER_TYPE);
  }

  /**
   * 根据项目 ID 删除全部普通用户关系。
   *
   * @param projectId 项目 ID
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteUserProjectByProjectId(
          Long projectId) {

    if (projectId == null) {
      return;
    }

    List<Long> affectedUserIds =
            userProjectDao
                    .selectUserIdListByProjectId(
                            projectId,
                            NORMAL_USER_TYPE);

    userProjectDao
            .deleteByProjectIdAndUserType(
                    projectId,
                    NORMAL_USER_TYPE);

    invalidateUsers(affectedUserIds);
  }

  /**
   * 根据项目 ID 删除全部负责人关系。
   *
   * @param projectId 项目 ID
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteOwnerProjectByProjectId(
          Long projectId) {

    if (projectId == null) {
      return;
    }

    List<Long> affectedUserIds =
            userProjectDao
                    .selectUserIdListByProjectId(
                            projectId,
                            OWNER_USER_TYPE);

    userProjectDao
            .deleteByProjectIdAndUserType(
                    projectId,
                    OWNER_USER_TYPE);

    invalidateUsers(affectedUserIds);
  }

  /**
   * 根据项目 ID 集合查询用户项目关系。
   *
   * @param projectIdList 项目 ID 列表
   * @return 用户项目关系列表
   */
  @Override
  public List<UserProject> lisUserProjectByProjectIds(
          List<Long> projectIdList) {

    List<Long> validProjectIds =
            normalizeIds(projectIdList);

    if (validProjectIds.isEmpty()) {
      return new ArrayList<>();
    }

    List<UserProject> userProjectList =
            userProjectDao.selectByProjectIds(
                    validProjectIds);

    return userProjectList == null
            ? new ArrayList<>()
            : userProjectList;
  }

  /**
   * 根据查询条件查询用户项目关系。
   *
   * @param userProjectDTO 查询条件
   * @return 用户项目关系列表
   */
  @Override
  public List<UserProject>
  lisUserProjectByUserProjectDTO(
          UserProjectDTO userProjectDTO) {

    if (userProjectDTO == null) {
      return new ArrayList<>();
    }

    List<UserProject> userProjectList =
            userProjectDao.select(
                    userProjectDTO);

    return userProjectList == null
            ? new ArrayList<>()
            : userProjectList;
  }

  /**
   * 保存指定类型的用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   * @param userType 用户类型
   */
  private void saveProjectRelation(
          Long projectId,
          List<Long> userIdList,
          int userType) {

    if (projectId == null) {
      return;
    }

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return;
    }

    List<UserProject> userProjectList =
            buildUserProjectList(
                    projectId,
                    validUserIds,
                    userType);

    userProjectDao.insertBatch(
            userProjectList);

    invalidateUsers(validUserIds);
  }

  /**
   * 删除指定类型的用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   * @param userType 用户类型
   */
  private void deleteProjectRelation(
          Long projectId,
          List<Long> userIdList,
          int userType) {

    if (projectId == null) {
      return;
    }

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return;
    }

    userProjectDao.deleteUserProject(
            buildUserProjectList(
                    projectId,
                    validUserIds,
                    userType));

    invalidateUsers(validUserIds);
  }

  /**
   * 增量补充指定类型的项目关联用户。
   *
   * @param projectId 项目 ID
   * @param userIdList 待关联用户 ID 列表
   * @param userType 用户类型
   */
  private void appendMissingProjectRelations(
          Long projectId,
          List<Long> userIdList,
          int userType) {

    if (projectId == null) {
      return;
    }

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return;
    }

    List<Long> existingUserIds =
            userProjectDao
                    .selectUserIdListByProjectId(
                            projectId,
                            userType);

    Set<Long> existingUserIdSet =
            CollectionUtils.isEmpty(
                    existingUserIds)
                    ? Collections.emptySet()
                    : new HashSet<>(
                    existingUserIds);

    List<Long> missingUserIds =
            validUserIds.stream()
                    .filter(userId ->
                            !existingUserIdSet.contains(
                                    userId))
                    .collect(Collectors.toList());

    if (missingUserIds.isEmpty()) {
      return;
    }

    saveProjectRelation(
            projectId,
            missingUserIds,
            userType);
  }

  /**
   * 构建用户项目关系列表。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   * @param userType 用户类型
   * @return 用户项目关系列表
   */
  private List<UserProject> buildUserProjectList(
          Long projectId,
          List<Long> userIdList,
          int userType) {

    List<UserProject> userProjectList =
            new ArrayList<>(userIdList.size());

    for (Long userId : userIdList) {
      UserProject userProject =
              new UserProject();

      userProject.setProjectId(projectId);
      userProject.setUserId(userId);
      userProject.setUserType(userType);

      userProjectList.add(userProject);
    }

    return userProjectList;
  }

  /**
   * 失效指定用户的授权快照。
   *
   * @param userIds 用户 ID 列表
   */
  private void invalidateUsers(
          List<Long> userIds) {

    normalizeIds(userIds)
            .forEach(permissionCache::invalidateUser);
  }

  /**
   * 过滤空 ID 并去重。
   *
   * @param idList ID 列表
   * @return 有效 ID 列表
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
