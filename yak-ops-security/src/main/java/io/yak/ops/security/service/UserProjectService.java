package io.yak.ops.security.service;

import io.yak.ops.security.common.dto.user.UserProjectDTO;
import io.yak.ops.security.common.entity.UserProject;
import io.yak.ops.security.common.enums.project.ProjectUserCode;

import java.util.List;

/**
 * 用户项目关系服务接口。
 *
 * @author weifuwan
 */
public interface UserProjectService {

  /**
   * 根据项目 ID 和用户类型查询用户 ID。
   *
   * @param projectId 项目 ID
   * @param projectUserCode 项目用户类型
   * @return 用户 ID 列表
   */
  List<Long> getUserIdListByProjectId(
          Long projectId,
          ProjectUserCode projectUserCode);

  /**
   * 根据用户 ID 集合查询项目 ID。
   *
   * @param userIdList 用户 ID 列表
   * @return 项目 ID 列表
   */
  List<Long> getProjectIdListByUserIdList(
          List<Long> userIdList);

  /**
   * 保存普通用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  void saveUserProject(
          Long projectId,
          List<Long> userIdList);

  /**
   * 删除普通用户项目关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  void delUserProject(
          Long projectId,
          List<Long> userIdList);

  /**
   * 保存项目负责人关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  void saveOwnerProject(
          Long projectId,
          List<Long> ownerIdList);

  /**
   * 删除项目负责人关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  void delOwnerProject(
          Long projectId,
          List<Long> ownerIdList);

  /**
   * 全量更新普通用户项目关系。
   *
   * <p>用户 ID 列表为空时，清空项目关联的全部普通用户。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  void updateUserProject(
          Long projectId,
          List<Long> userIdList);

  /**
   * 增量补充项目关联的普通用户。
   *
   * <p>只新增尚未关联的用户，不删除已有关系。
   *
   * @param projectId 项目 ID
   * @param userIdList 用户 ID 列表
   */
  void updateUserInformationAssociatedWithProject(
          Long projectId,
          List<Long> userIdList);

  /**
   * 根据项目 ID 删除全部普通用户关系。
   *
   * @param projectId 项目 ID
   */
  void deleteUserProjectByProjectId(
          Long projectId);

  /**
   * 根据项目 ID 删除全部负责人关系。
   *
   * @param projectId 项目 ID
   */
  void deleteOwnerProjectByProjectId(
          Long projectId);

  /**
   * 全量更新项目负责人关系。
   *
   * <p>负责人 ID 列表为空时，清空项目关联的全部负责人。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  void updateOwnerProject(
          Long projectId,
          List<Long> ownerIdList);

  /**
   * 增量补充项目关联的负责人。
   *
   * <p>只新增尚未关联的负责人，不删除已有关系。
   *
   * @param projectId 项目 ID
   * @param ownerIdList 负责人 ID 列表
   */
  void updateOwnerInformationAssociatedWithProject(
          Long projectId,
          List<Long> ownerIdList);

  /**
   * 根据项目 ID 集合查询用户项目关系。
   *
   * <p>保留原有方法名称，避免影响现有调用方。
   *
   * @param projectIdList 项目 ID 列表
   * @return 用户项目关系列表
   */
  List<UserProject> lisUserProjectByProjectIds(
          List<Long> projectIdList);

  /**
   * 根据查询条件查询用户项目关系。
   *
   * <p>保留原有方法名称，避免影响现有调用方。
   *
   * @param userProjectDTO 查询条件
   * @return 用户项目关系列表
   */
  List<UserProject> lisUserProjectByUserProjectDTO(
          UserProjectDTO userProjectDTO);
}