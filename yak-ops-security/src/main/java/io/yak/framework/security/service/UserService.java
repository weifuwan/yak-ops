package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.vo.role.AssignInfoVO;
import io.yak.framework.security.common.vo.user.CurrentUserVO;
import io.yak.framework.security.common.vo.user.UserBasicVO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.exception.YakSecurityException;

import java.util.List;

/**
 * 用户服务接口。
 *
 * @author weifuwan
 */
public interface UserService {

  /**
   * 校验指定用户字段是否可用。
   *
   * @param checkType 校验类型
   * @param checkValue 校验值
   * @return 校验结果
   */
  Result<Void> check(
          Integer checkType,
          String checkValue);

  /**
   * 分页查询用户详细信息。
   *
   * @param queryDTO 查询条件
   * @return 用户分页数据
   */
  PagingData<UserVO> getUserPage(
          UserQueryDTO queryDTO);

  /**
   * 分页查询用户简要信息。
   *
   * @param queryDTO 查询条件
   * @return 用户简要分页数据
   */
  PagingData<UserBriefVO> getUserBriefPage(
          UserBriefQueryDTO queryDTO);

  /**
   * 根据用户 ID 查询用户详情。
   *
   * @param userId 用户 ID
   * @return 用户详情
   */
  UserVO getUserDetailByUserId(
          Long userId);

  /**
   * 根据用户 ID 删除用户。
   *
   * @param userId 用户 ID
   * @return 删除结果
   */
  Result<Void> deleteByUserId(
          Long userId);

  /**
   * 根据用户名查询用户简要信息。
   *
   * @param username 用户名
   * @return 用户简要信息
   */
  UserBriefVO getUserBriefByUsername(
          String username);

  /**
   * 根据用户名查询用户实体。
   *
   * @param username 用户名
   * @return 用户实体
   */
  User getUserByUsername(
          String username);

  /**
   * 根据用户 ID 集合查询用户简要信息。
   *
   * @param userIds 用户 ID 集合
   * @return 用户简要信息列表
   */
  List<UserBriefVO> getUserBriefListByUserIds(
          List<Long> userIds);

  /**
   * 根据部门 ID 查询用户简要信息。
   *
   * <p>查询范围包含当前部门以及所有子部门。
   *
   * @param deptId 部门 ID
   * @return 用户简要信息列表
   */
  List<UserBriefVO> getUserBriefListByDeptId(
          Long deptId);

  /**
   * 根据用户 ID 查询角色分配信息。
   *
   * @param userId 用户 ID
   * @return 角色分配信息列表
   * @throws YakSecurityException 用户参数或数据异常
   */
  List<AssignInfoVO> getAssignInfoListByUserId(
          Long userId)
          throws YakSecurityException;

  /**
   * 根据角色 ID 查询用户简要信息。
   *
   * @param roleId 角色 ID
   * @return 用户简要信息列表
   */
  List<UserBriefVO> getUserBriefListByRoleId(
          Long roleId);

  /**
   * 根据用户名或真实姓名模糊查询用户。
   *
   * @param keyword 查询关键字
   * @return 用户简要信息列表
   */
  List<UserBriefVO> searchUserBriefList(
          String keyword);

  /**
   * 查询全部用户简要信息并按创建时间排序。
   *
   * @param ascending 是否升序
   * @return 用户简要信息列表
   */
  List<UserBriefVO> getAllUserBriefListOrderByCreateTime(
          boolean ascending);

  /**
   * 根据用户名或真实姓名查询用户 ID。
   *
   * @param keyword 查询关键字
   * @return 用户 ID 列表
   */
  List<Long> searchUserIds(
          String keyword);

  /**
   * 查询全部用户简要信息。
   *
   * @return 用户简要信息列表
   */
  List<UserBriefVO> getAllUserBriefList();

  /**
   * 新增用户。
   *
   * @param userDTO 用户信息
   * @param operator 操作人
   * @return 新增结果
   */
  Result<Void> addUser(
          UserDTO userDTO,
          String operator);

  /**
   * 编辑用户。
   *
   * @param userDTO 用户信息
   * @param operator 操作人
   * @return 编辑结果
   */
  Result<Void> editUser(
          UserDTO userDTO,
          String operator);

  /**
   * 根据用户 ID 集合批量查询用户详情。
   *
   * @param userIds 用户 ID 集合
   * @return 用户详情列表
   */
  Result<List<UserVO>> getUserDetailsByUserIds(
          List<Long> userIds);

  /**
   * 根据用户 ID 集合查询用户基础信息。
   *
   * @param userIds 用户 ID 集合
   * @return 用户基础信息列表
   */
  List<UserBasicVO> getUserBasicListByUserIds(
          List<Long> userIds);

  CurrentUserVO getCurrentUserByUsername(
          String username);
}