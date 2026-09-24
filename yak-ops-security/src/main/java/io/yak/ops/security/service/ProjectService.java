package io.yak.ops.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.project.ProjectBriefQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectQueryDTO;
import io.yak.ops.security.common.dto.project.ProjectSaveDTO;
import io.yak.ops.security.common.vo.project.ProjectBriefVO;
import io.yak.ops.security.common.vo.project.ProjectBriefVOWithUser;
import io.yak.ops.security.common.vo.project.ProjectDeleteCheckVO;
import io.yak.ops.security.common.vo.project.ProjectVO;
import io.yak.ops.security.common.vo.user.UserBriefVO;
import io.yak.ops.security.exception.YakSecurityException;

import java.util.List;

/**
 * 项目服务接口。
 *
 * @author weifuwan
 */
public interface ProjectService {

  /**
   * 创建项目。
   *
   * @param projectSaveDTO 项目信息
   * @param operator 操作人
   * @return 创建后的项目详情
   * @throws YakSecurityException 项目参数异常
   */
  ProjectVO createProject(
          ProjectSaveDTO projectSaveDTO,
          String operator)
          throws YakSecurityException;

  /**
   * 根据项目 ID 查询项目详情。
   *
   * @param projectId 项目 ID
   * @return 项目详情
   * @throws YakSecurityException 项目不存在
   */
  ProjectVO getProjectDetailByProjectId(
          Long projectId)
          throws YakSecurityException;

  /**
   * 根据项目 ID 查询项目简要信息。
   *
   * @param projectId 项目 ID
   * @return 项目简要信息
   */
  ProjectBriefVO getProjectBriefByProjectId(
          Long projectId);

  /**
   * 分页查询项目。
   *
   * @param queryDTO 查询条件
   * @return 项目分页数据
   */
  PagingData<ProjectVO> getProjectPage(
          ProjectQueryDTO queryDTO);

  /**
   * 分页查询项目。
   *
   * <p>额外项目 ID 将与查询条件匹配的项目进行合并。
   *
   * @param queryDTO 查询条件
   * @param projectIdList 额外项目 ID 列表
   * @return 项目分页数据
   */
  PagingData<ProjectVO> getProjectPage(
          ProjectQueryDTO queryDTO,
          List<Long> projectIdList);

  /**
   * 根据项目 ID 删除项目。
   *
   * @param projectId 项目 ID
   * @param operator 操作人
   */
  void deleteProjectByProjectId(
          Long projectId,
          String operator);

  /**
   * 更新项目。
   *
   * @param projectSaveDTO 项目信息
   * @param operator 操作人
   * @throws YakSecurityException 项目不存在或参数异常
   */
  void updateProject(
          ProjectSaveDTO projectSaveDTO,
          String operator)
          throws YakSecurityException;

  /**
   * 变更项目状态。
   *
   * @param projectId 项目 ID
   * @param operator 操作人
   */
  void changeProjectStatus(
          Long projectId,
          String operator);

  /**
   * 添加项目用户。
   *
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @param operator 操作人
   */
  void addProjectUser(
          Long projectId,
          Long userId,
          String operator);

  /**
   * 删除项目用户。
   *
   * @param projectId 项目 ID
   * @param userId 用户 ID
   * @param operator 操作人
   */
  void delProjectUser(
          Long projectId,
          Long userId,
          String operator);

  /**
   * 添加项目负责人。
   *
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @param operator 操作人
   */
  void addProjectOwner(
          Long projectId,
          Long ownerId,
          String operator);

  /**
   * 删除项目负责人。
   *
   * @param projectId 项目 ID
   * @param ownerId 负责人 ID
   * @param operator 操作人
   */
  void delProjectOwner(
          Long projectId,
          Long ownerId,
          String operator);

  /**
   * 查询全部项目简要信息。
   *
   * @return 项目简要信息列表
   */
  List<ProjectBriefVO> getProjectBriefList();

  /**
   * 执行项目删除前校验。
   *
   * @param projectId 项目 ID
   * @return 删除校验结果
   */
  ProjectDeleteCheckVO checkBeforeDelete(
          Long projectId);

  /**
   * 分页查询项目简要信息。
   *
   * @param queryDTO 查询条件
   * @return 项目简要分页数据
   */
  PagingData<ProjectBriefVO> getProjectBriefPage(
          ProjectBriefQueryDTO queryDTO);

  /**
   * 校验项目是否存在。
   *
   * @param projectId 项目 ID
   * @return 项目是否存在
   */
  boolean checkProjectExist(
          Long projectId);

  /**
   * 根据项目 ID 查询未分配用户。
   *
   * @param projectId 项目 ID
   * @return 未分配用户列表
   * @throws YakSecurityException 项目不存在
   */
  Result<List<UserBriefVO>> unassignedByProjectId(
          Long projectId)
          throws YakSecurityException;

  /**
   * 根据用户 ID 查询项目简要信息。
   *
   * @param userId 用户 ID
   * @return 项目简要信息列表
   */
  Result<List<ProjectBriefVO>>
  getProjectBriefByUserId(
          Long userId);

  /**
   * 根据项目 ID 集合查询包含用户信息的项目简要信息。
   *
   * @param projectIdList 项目 ID 列表
   * @return 包含用户信息的项目列表
   */
  List<ProjectBriefVOWithUser>
  listProjectBriefVOWithUserByProjectIds(
          List<Long> projectIdList);
}