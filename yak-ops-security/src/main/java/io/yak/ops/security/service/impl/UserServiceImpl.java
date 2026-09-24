package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.user.UserBriefQueryDTO;
import io.yak.ops.security.common.dto.user.UserDTO;
import io.yak.ops.security.common.dto.user.UserQueryDTO;
import io.yak.ops.security.common.entity.BaseEntity;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.common.entity.user.User;
import io.yak.ops.security.common.entity.user.UserBrief;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.enums.user.UserCheckType;
import io.yak.ops.security.common.po.UserPO;
import io.yak.ops.security.common.po.UserProjectPO;
import io.yak.ops.security.common.vo.project.ProjectBriefVO;
import io.yak.ops.security.common.vo.role.AssignInfoVO;
import io.yak.ops.security.common.vo.role.RoleBriefVO;
import io.yak.ops.security.common.vo.user.CurrentUserVO;
import io.yak.ops.security.common.vo.user.UserBasicVO;
import io.yak.ops.security.common.vo.user.UserBriefVO;
import io.yak.ops.security.common.vo.user.UserVO;
import io.yak.ops.security.dao.*;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.service.DeptService;
import io.yak.ops.security.service.PermissionService;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.RoleService;
import io.yak.ops.security.service.UserRoleService;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.util.CopyBeanUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityUserServiceImpl")
public class UserServiceImpl implements UserService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(UserServiceImpl.class);

  private static final Pattern USER_NAME_PATTERN =
          Pattern.compile("^[0-9a-zA-Z_]{5,50}$");

  private static final Pattern USER_PHONE_PATTERN =
          Pattern.compile(
                  "^(13[0-9]|14[01456879]|15[0-35-9]"
                          + "|16[2567]|17[0-8]|18[0-9]"
                          + "|19[0-35-9])\\d{8}$");

  private static final Pattern USER_MAIL_PATTERN =
          Pattern.compile(
                  "^\\w+([-+.]\\w+)*"
                          + "@\\w+([-.]\\w+)*"
                          + "\\.\\w+([-.]\\w+)*$");

  private final UserDao userDao;

  private final PermissionService permissionService;

  private final RolePermissionService rolePermissionService;

  private final DeptService deptService;

  private final RoleService roleService;

  private final UserRoleService userRoleService;

  private final UserProjectDao userProjectDao;

  private final UserResourceDao userResourceDao;

  private final ProjectDao projectDao;

  private final PasswordEncoder passwordEncoder;

  private final PermissionDao permissionDao;

  /**
   * 创建用户服务。
   *
   * @param userDao 用户数据访问对象
   * @param permissionService 权限服务
   * @param rolePermissionService 角色权限服务
   * @param deptService 部门服务
   * @param roleService 角色服务
   * @param userRoleService 用户角色服务
   * @param userProjectDao 用户项目数据访问对象
   * @param userResourceDao 用户资源数据访问对象
   * @param projectDao 项目数据访问对象
   * @param passwordEncoder 密码编码器
   */
  public UserServiceImpl(
          UserDao userDao,
          PermissionService permissionService,
          RolePermissionService rolePermissionService,
          DeptService deptService,
          RoleService roleService,
          UserRoleService userRoleService,
          UserProjectDao userProjectDao,
          UserResourceDao userResourceDao,
          ProjectDao projectDao,
          PasswordEncoder passwordEncoder,
          PermissionDao permissionDao
          ) {

    this.userDao = userDao;
    this.permissionService = permissionService;
    this.rolePermissionService = rolePermissionService;
    this.deptService = deptService;
    this.roleService = roleService;
    this.userRoleService = userRoleService;
    this.userProjectDao = userProjectDao;
    this.userResourceDao = userResourceDao;
    this.projectDao = projectDao;
    this.passwordEncoder = passwordEncoder;
    this.permissionDao = permissionDao;
  }

  @Override
  public CurrentUserVO getCurrentUserByUsername(
          String username) {

    UserBriefVO basic =
            getUserBriefByUsername(username);

    if (basic == null) {
      return null;
    }

    CurrentUserVO currentUser =
            CopyBeanUtil.copy(
                    basic,
                    CurrentUserVO.class);

    if (currentUser == null) {
      throw new IllegalStateException(
              "当前用户对象转换失败");
    }

    List<Long> roleIds =
            userRoleService.getRoleIdListByUserId(
                    basic.getId());

    if (CollectionUtils.isEmpty(roleIds)) {
      currentUser.setPermissionCodes(
              Collections.emptyList());
      return currentUser;
    }

    List<Long> permissionIds =
            rolePermissionService
                    .getPermissionIdListByRoleIdList(
                            roleIds);

    if (CollectionUtils.isEmpty(permissionIds)) {
      currentUser.setPermissionCodes(
              Collections.emptyList());
      return currentUser;
    }

    Set<Long> permissionIdSet =
            new HashSet<>(permissionIds);

    List<String> permissionCodes =
            permissionDao
                    .selectAllAndAscOrderByLevel()
                    .stream()
                    .filter(permission ->
                            permissionIdSet.contains(
                                    permission.getId()))
                    .filter(permission ->
                            Boolean.TRUE.equals(
                                    permission.getActive()))
                    .map(Permission::getPermissionCode)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());

    currentUser.setPermissionCodes(
            permissionCodes);

    return currentUser;
  }

  /**
   * 校验指定用户字段是否可用。
   *
   * @param checkType 校验类型
   * @param checkValue 校验值
   * @return 校验结果
   */
  @Override
  public Result<Void> check(
          Integer checkType,
          String checkValue) {

    if (checkType == null) {
      return Result.buildParamIllegal(
              "校验类型不能为空");
    }

    if (Objects.equals(
            checkType,
            UserCheckType.USER_NAME.getCode())) {

      return userNameCheck(checkValue);
    }

    if (Objects.equals(
            checkType,
            UserCheckType.USER_PHONE.getCode())) {

      return userPhoneCheck(checkValue);
    }

    if (Objects.equals(
            checkType,
            UserCheckType.USER_MAIL.getCode())) {

      return userMailCheck(checkValue);
    }

    return Result.buildParamIllegal(
            "校验类型参数不正确");
  }

  /**
   * 分页查询用户详细信息。
   *
   * @param queryDTO 查询条件
   * @return 用户分页数据
   */
  @Override
  public PagingData<UserVO> getUserPage(
          UserQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "用户查询条件不能为空");
    }

    List<Long> userIdList = null;

    if (queryDTO.getRoleId() != null) {
      userIdList =
              userRoleService.getUserIdListByRoleId(
                      queryDTO.getRoleId());

      if (CollectionUtils.isEmpty(userIdList)) {
        return PagingData.from(
                PageData.empty(
                        queryDTO.getPage(),
                        queryDTO.getSize()));
      }
    }

    IPage<User> userPage =
            userDao.selectPageByUserIdList(
                    queryDTO,
                    userIdList);

    List<User> userList =
            userPage.getRecords();

    if (CollectionUtils.isEmpty(userList)) {
      return toPagingData(
              new ArrayList<>(),
              userPage);
    }

    List<Long> userIds =
            userList.stream()
                    .map(BaseEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

    Map<Long, List<ProjectBriefVO>>
            userProjectMap =
            buildUserProjectMap(userIds);

    Map<Long, List<RoleBriefVO>>
            userRoleMap =
            roleService.getRoleBriefListByUserIds(
                    userIds);

    if (userRoleMap == null) {
      userRoleMap = Collections.emptyMap();
    }

    List<UserVO> userVOList =
            new ArrayList<>(userList.size());

    for (User user : userList) {
      UserVO userVO =
              CopyBeanUtil.copy(
                      user,
                      UserVO.class);

      if (userVO == null) {
        continue;
      }

      userVO.setRoleList(
              userRoleMap.getOrDefault(
                      user.getId(),
                      Collections.emptyList()));

      userVO.setProjectList(
              userProjectMap.getOrDefault(
                      user.getId(),
                      Collections.emptyList()));

      userVO.setUpdateTime(
              user.getUpdateTime());

      userVO.setCreateTime(
              user.getCreateTime());

      privacyProcessing(userVO);

      userVOList.add(userVO);
    }

    return toPagingData(
            userVOList,
            userPage);
  }

  /**
   * 分页查询用户简要信息。
   *
   * @param queryDTO 查询条件
   * @return 用户简要分页数据
   */
  @Override
  public PagingData<UserBriefVO> getUserBriefPage(
          UserBriefQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "用户查询条件不能为空");
    }

    List<Long> deptIdList =
            deptService
                    .getDeptIdListByParentIdAndDeptName(
                            queryDTO.getDeptId(),
                            queryDTO.getDeptName());

    IPage<UserBrief> userPage =
            userDao.selectBriefPageByDeptIdList(
                    queryDTO,
                    deptIdList);

    List<UserBriefVO> userList =
            CopyBeanUtil.copyList(
                    userPage.getRecords(),
                    UserBriefVO.class);

    return toPagingData(
            userList,
            userPage);
  }

  /**
   * 根据用户 ID 查询用户详情。
   *
   * @param userId 用户 ID
   * @return 用户详情
   */
  @Override
  public UserVO getUserDetailByUserId(
          Long userId) {

    if (userId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    User user =
            userDao.selectByUserId(userId);

    if (user == null) {
      throw new YakSecurityException(
              ResultCode.USER_NOT_EXISTS);
    }

    UserVO userVO =
            CopyBeanUtil.copy(
                    user,
                    UserVO.class);

    if (userVO == null) {
      throw new IllegalStateException(
              "用户对象转换失败");
    }

    enrichUserRoleAndPermission(
            userVO);

    Map<Long, List<ProjectBriefVO>>
            userProjectMap =
            buildUserProjectMap(
                    Collections.singletonList(
                            userId));

    userVO.setProjectList(
            userProjectMap.getOrDefault(
                    userId,
                    Collections.emptyList()));

    userVO.setUpdateTime(
            user.getUpdateTime());

    userVO.setCreateTime(
            user.getCreateTime());

    return userVO;
  }

  /**
   * 根据用户 ID 集合查询用户基础信息。
   *
   * @param userIds 用户 ID 集合
   * @return 用户基础信息列表
   */
  @Override
  public List<UserBasicVO> getUserBasicListByUserIds(
          List<Long> userIds) {

    if (CollectionUtils.isEmpty(userIds)) {
      return new ArrayList<>();
    }

    return CopyBeanUtil.copyList(
            userDao.selectBriefListByUserIdList(
                    userIds),
            UserBasicVO.class);
  }



  /**
   * 根据用户 ID 集合批量查询用户详情。
   *
   * @param userIds 用户 ID 集合
   * @return 用户详情列表
   */
  @Override
  public Result<List<UserVO>> getUserDetailsByUserIds(
          List<Long> userIds) {

    /*
     * 原代码这里写成了 !CollectionUtils.isEmpty(ids)，
     * 导致传入有效 ID 时直接返回空列表。
     */
    if (CollectionUtils.isEmpty(userIds)) {
      return Result.success(
              new ArrayList<>());
    }

    List<Long> distinctUserIds =
            userIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

    if (distinctUserIds.isEmpty()) {
      return Result.success(
              new ArrayList<>());
    }

    List<UserVO> userVOList =
            distinctUserIds.stream()
                    .map(userDao::selectByUserId)
                    .filter(Objects::nonNull)
                    .map(user -> {
                      UserVO userVO =
                              CopyBeanUtil.copy(
                                      user,
                                      UserVO.class);

                      if (userVO != null) {
                        userVO.setUpdateTime(
                                user.getUpdateTime());

                        userVO.setCreateTime(
                                user.getCreateTime());
                      }

                      return userVO;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

    Map<Long, List<ProjectBriefVO>>
            userProjectMap =
            buildUserProjectMap(
                    distinctUserIds);

    for (UserVO userVO : userVOList) {
      enrichUserRoleAndPermission(
              userVO);

      userVO.setProjectList(
              userProjectMap.getOrDefault(
                      userVO.getId(),
                      Collections.emptyList()));
    }

    return Result.success(userVOList);
  }

  /**
   * 根据用户 ID 删除用户。
   *
   * @param userId 用户 ID
   * @return 删除结果
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> deleteByUserId(
          Long userId) {

    if (userId == null) {
      return Result.buildParamIllegal(
              "用户 ID 不能为空");
    }

    userRoleService.deleteByUserIdOrRoleId(
            userId,
            null);
    userProjectDao.deleteByUserId(userId);
    userResourceDao.deleteByUserId(userId, null);

    boolean success =
            userDao.deleteByUserId(userId);

    if (!success) {
      return Result.fail();
    }

    return Result.success();
  }

  /**
   * 根据用户名查询用户简要信息。
   *
   * @param username 用户名
   * @return 用户简要信息
   */
  @Override
  public UserBriefVO getUserBriefByUsername(
          String username) {

    if (!StringUtils.hasText(username)) {
      return null;
    }

    User user =
            userDao.selectByUsername(
                    username);

    return CopyBeanUtil.copy(
            user,
            UserBriefVO.class);
  }
  /**
   * 根据用户名查询用户实体。
   *
   * @param username 用户名
   * @return 用户实体
   */
  @Override
  public User getUserByUsername(
          String username) {

    if (!StringUtils.hasText(username)) {
      return null;
    }

    return userDao.selectByUsername(
            username);
  }

  /**
   * 根据用户 ID 集合查询用户简要信息。
   *
   * @param userIds 用户 ID 集合
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO>
  getUserBriefListByUserIds(
          List<Long> userIds) {

    if (CollectionUtils.isEmpty(userIds)) {
      return new ArrayList<>();
    }

    List<UserBrief> userBriefList =
            userDao.selectBriefListByUserIdList(
                    userIds);

    List<UserBriefVO> userBriefVOList =
            CopyBeanUtil.copyList(
                    userBriefList,
                    UserBriefVO.class);

    if (CollectionUtils.isEmpty(
            userBriefVOList)) {

      return new ArrayList<>();
    }

    /*
     * 此处沿用原有逻辑。
     *
     * 后续建议在 UserDao 增加批量查询完整用户信息的方法，
     * 避免循环查询产生 N+1 SQL。
     */
    for (UserBriefVO userBriefVO
            : userBriefVOList) {

      User user =
              userDao.selectByUserId(
                      userBriefVO.getId());

      if (user != null) {
        userBriefVO.setEmail(
                user.getEmail());

        userBriefVO.setPhone(
                user.getPhone());
      }

      List<String> roleNameList =
              roleService
                      .getRoleBriefListByUserId(
                              userBriefVO.getId())
                      .stream()
                      .map(RoleBriefVO::getRoleName)
                      .collect(Collectors.toList());

      userBriefVO.setRoleList(
              roleNameList);
    }

    return userBriefVOList;
  }

  /**
   * 根据用户名或真实姓名模糊查询用户。
   *
   * @param keyword 查询关键字
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO> searchUserBriefList(
          String keyword) {

    List<UserBrief> userList =
            userDao
                    .selectBriefListByNameAndDescOrderByCreateTime(
                            keyword);

    return CopyBeanUtil.copyList(
            userList,
            UserBriefVO.class);
  }

  /**
   * 查询全部用户简要信息并按创建时间排序。
   *
   * @param ascending 是否升序
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO>
  getAllUserBriefListOrderByCreateTime(
          boolean ascending) {

    List<UserBrief> userList =
            userDao.selectBriefListOrderByCreateTime(
                    ascending);

    return CopyBeanUtil.copyList(
            userList,
            UserBriefVO.class);
  }

  /**
   * 根据用户名或真实姓名查询用户 ID。
   *
   * @param keyword 查询关键字
   * @return 用户 ID 列表
   */
  @Override
  public List<Long> searchUserIds(
          String keyword) {

    List<Long> userIds =
            userDao
                    .selectUserIdListByUsernameOrRealName(
                            keyword);

    return userIds == null
            ? new ArrayList<>()
            : userIds;
  }

  /**
   * 查询全部用户简要信息。
   *
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO> getAllUserBriefList() {
    List<UserBrief> userList =
            userDao.selectAllBriefList();

    return CopyBeanUtil.copyList(
            userList,
            UserBriefVO.class);
  }

  /**
   * 根据部门 ID 查询用户简要信息。
   *
   * @param deptId 部门 ID
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO> getUserBriefListByDeptId(
          Long deptId) {

    List<Long> deptIdList =
            deptService
                    .getDeptIdListByParentId(
                            deptId);

    if (CollectionUtils.isEmpty(deptIdList)) {
      return new ArrayList<>();
    }

    List<UserBrief> userList =
            userDao.selectBriefListByDeptIdList(
                    deptIdList);

    return CopyBeanUtil.copyList(
            userList,
            UserBriefVO.class);
  }

  /**
   * 根据用户 ID 查询角色分配信息。
   *
   * @param userId 用户 ID
   * @return 角色分配信息列表
   */
  @Override
  public List<AssignInfoVO>
  getAssignInfoListByUserId(
          Long userId) {

    if (userId == null) {
      throw new YakSecurityException(
              ResultCode.USER_ID_CANNOT_BE_NULL);
    }

    List<RoleBriefVO> roleList =
            roleService.getAllRoleBriefList();

    if (CollectionUtils.isEmpty(roleList)) {
      return new ArrayList<>();
    }

    List<Long> assignedRoleIds =
            userRoleService
                    .getRoleIdListByUserId(
                            userId);

    Set<Long> assignedRoleIdSet =
            CollectionUtils.isEmpty(
                    assignedRoleIds)
                    ? Collections.emptySet()
                    : new HashSet<>(
                    assignedRoleIds);

    List<AssignInfoVO> assignInfoList =
            new ArrayList<>(roleList.size());

    for (RoleBriefVO role : roleList) {
      AssignInfoVO assignInfo =
              new AssignInfoVO();

      assignInfo.setId(role.getId());
      assignInfo.setName(
              role.getRoleName());

      assignInfo.setHas(
              assignedRoleIdSet.contains(
                      role.getId()));

      assignInfoList.add(assignInfo);
    }

    return assignInfoList;
  }

  /**
   * 根据角色 ID 查询用户简要信息。
   *
   * @param roleId 角色 ID
   * @return 用户简要信息列表
   */
  @Override
  public List<UserBriefVO> getUserBriefListByRoleId(
          Long roleId) {

    if (roleId == null) {
      return new ArrayList<>();
    }

    List<Long> userIdList =
            userRoleService
                    .getUserIdListByRoleId(
                            roleId);

    if (CollectionUtils.isEmpty(userIdList)) {
      return new ArrayList<>();
    }

    List<UserBrief> userList =
            userDao.selectBriefListByUserIdList(
                    userIdList);

    return CopyBeanUtil.copyList(
            userList,
            UserBriefVO.class);
  }

  /**
   * 新增用户。
   *
   * @param userDTO 用户信息
   * @param operator 操作人
   * @return 新增结果
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> addUser(
          UserDTO userDTO,
          String operator) {

    Result<Void> checkResult =
            checkUserParam(
                    userDTO,
                    true);

    if (checkResult.failed()) {
      return checkResult;
    }
    if (userDao.selectByUsername(
            userDTO.getUserName()) != null) {

      return Result.fail(
              ResultCode.USER_ACCOUNT_ALREADY_EXIST);
    }

    try {
      UserPO userPO =
              CopyBeanUtil.copy(
                      userDTO,
                      UserPO.class);

      if (userPO == null) {
        throw new IllegalStateException(
                "用户对象转换失败");
      }

      userPO.setPw(
              passwordEncoder.encode(
                      userDTO.getPw()));

      int affectedRows =
              userDao.addUser(userPO);

      if (affectedRows != 1) {
        return Result.fail(
                ResultCode.USER_ACCOUNT_INSERT_FAIL);
      }

      userRoleService.updateUserRoleByUserId(
              userPO.getId(),
              userDTO.getRoleIds());

      LOGGER.info(
              "新增用户成功，用户ID={}，用户名={}，操作人={}",
              userPO.getId(),
              userDTO.getUserName(),
              operator);

      return Result.success();
    } catch (Exception exception) {
      LOGGER.error(
              "新增用户失败，用户名={}，操作人={}",
              userDTO.getUserName(),
              operator,
              exception);

      throw new YakSecurityException(
              ResultCode.USER_ACCOUNT_INSERT_FAIL,
              exception);
    }
  }

  /**
   * 编辑用户。
   *
   * @param userDTO 用户信息
   * @param operator 操作人
   * @return 编辑结果
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> editUser(
          UserDTO userDTO,
          String operator) {

    Result<Void> checkResult =
            checkUserParam(
                    userDTO,
                    false);

    if (checkResult.failed()) {
      return checkResult;
    }

    User currentUser =
            userDao.selectByUsername(
                    userDTO.getUserName());

    if (currentUser == null) {
      return Result.fail(
              ResultCode.USER_ACCOUNT_NOT_EXIST);
    }

    try {
      UserPO userPO =
              CopyBeanUtil.copy(
                      userDTO,
                      UserPO.class);

      if (userPO == null) {
        throw new IllegalStateException(
                "用户对象转换失败");
      }

      userPO.setId(
              currentUser.getId());

      if (StringUtils.hasText(
              userDTO.getPw())) {

        userPO.setPw(
                passwordEncoder.encode(
                        userDTO.getPw()));
      } else {
        /*
         * 密码为空表示不修改密码。
         */
        userPO.setPw(null);
      }

      int affectedRows =
              userDao.editUser(userPO);

      if (affectedRows != 1) {
        return Result.fail(
                ResultCode.USER_ACCOUNT_UPDATE_FAIL);
      }

      userRoleService.updateUserRoleByUserId(
              userPO.getId(),
              userDTO.getRoleIds());

      LOGGER.info(
              "编辑用户成功，用户ID={}，用户名={}，操作人={}",
              userPO.getId(),
              userDTO.getUserName(),
              operator);

      return Result.success();
    } catch (Exception exception) {
      LOGGER.error(
              "编辑用户失败，用户名={}，操作人={}",
              userDTO.getUserName(),
              operator,
              exception);

      throw new YakSecurityException(
              ResultCode.USER_ACCOUNT_UPDATE_FAIL,
              exception);
    }
  }

  /**
   * 为用户详情填充角色和权限信息。
   *
   * @param userVO 用户详情
   */
  private void enrichUserRoleAndPermission(
          UserVO userVO) {

    List<RoleBriefVO> roleList =
            roleService.getRoleBriefListByUserId(
                    userVO.getId());

    if (roleList == null) {
      roleList = new ArrayList<>();
    }

    userVO.setRoleList(roleList);

    List<Long> roleIds =
            roleList.stream()
                    .map(RoleBriefVO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

    List<Long> permissionIds =
            roleIds.isEmpty()
                    ? new ArrayList<>()
                    : rolePermissionService
                    .getPermissionIdListByRoleIdList(
                            roleIds);

    if (permissionIds == null) {
      permissionIds = new ArrayList<>();
    }

    userVO.setPermissionTreeVO(
            permissionService
                    .buildPermissionTreeWithHas(
                            permissionIds));
  }

  /**
   * 查询用户关联的项目并按照用户 ID 分组。
   *
   * @param userIds 用户 ID 集合
   * @return 用户与项目列表映射
   */
  private Map<Long, List<ProjectBriefVO>>
  buildUserProjectMap(
          List<Long> userIds) {

    if (CollectionUtils.isEmpty(userIds)) {
      return Collections.emptyMap();
    }

    List<UserProjectPO> userProjectList =
            userProjectDao
                    .selectProjectListByUserIdList(
                            userIds);

    if (CollectionUtils.isEmpty(
            userProjectList)) {

      return Collections.emptyMap();
    }

    List<Long> projectIds =
            userProjectList.stream()
                    .map(UserProjectPO::getProjectId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

    if (projectIds.isEmpty()) {
      return Collections.emptyMap();
    }

    List<ProjectBriefVO> projectList =
            CopyBeanUtil.copyList(
                    projectDao
                            .selectProjectBriefByProjectIds(
                                    projectIds),
                    ProjectBriefVO.class);

    if (CollectionUtils.isEmpty(projectList)) {
      return Collections.emptyMap();
    }

    Map<Long, ProjectBriefVO> projectMap =
            projectList.stream()
                    .filter(Objects::nonNull)
                    .filter(project ->
                            project.getId() != null)
                    .collect(
                            Collectors.toMap(
                                    ProjectBriefVO::getId,
                                    project -> project,
                                    (first, second) -> first,
                                    LinkedHashMap::new));

    Map<Long, List<ProjectBriefVO>>
            userProjectMap =
            new HashMap<>();

    for (UserProjectPO userProject
            : userProjectList) {

      ProjectBriefVO project =
              projectMap.get(
                      userProject.getProjectId());

      if (project == null) {
        continue;
      }

      userProjectMap
              .computeIfAbsent(
                      userProject.getUserId(),
                      key -> new ArrayList<>())
              .add(project);
    }

    return userProjectMap;
  }

  /**
   * 用户敏感信息脱敏。
   *
   * @param userVO 用户信息
   */
  private void privacyProcessing(
          UserVO userVO) {

    if (!StringUtils.hasText(
            userVO.getPhone())) {

      return;
    }

    userVO.setPhone(
            userVO.getPhone().replaceAll(
                    "(\\d{3})\\d{4}(\\d{4})",
                    "$1****$2"));
  }

  /**
   * 将持久化分页元数据转换为统一 HTTP 分页数据。
   */
  private static <T> PagingData<T> toPagingData(
          List<T> records,
          IPage<?> page) {
    return PagingData.from(
            new PageData<>(
                    records,
                    page.getTotal(),
                    page.getPages(),
                    page.getCurrent(),
                    page.getSize()));
  }

  /**
   * 校验用户基础参数。
   *
   * @param userDTO 用户信息
   * @param passwordRequired 是否必须提供密码
   * @return 参数校验结果
   */
  private Result<Void> checkUserParam(
          UserDTO userDTO,
          boolean passwordRequired) {

    if (userDTO == null) {
      return Result.buildParamIllegal(
              "用户信息不能为空");
    }

    if (!StringUtils.hasText(
            userDTO.getUserName())) {

      return Result.buildParamIllegal(
              "用户名不能为空");
    }

    if (passwordRequired
            && !StringUtils.hasText(
            userDTO.getPw())) {

      return Result.buildParamIllegal(
              "用户密码不能为空");
    }

    return Result.success(null);
  }

  /**
   * 校验用户名。
   *
   * @param username 用户名
   * @return 校验结果
   */
  private Result<Void> userNameCheck(
          String username) {

    if (!StringUtils.hasText(username)
            || !USER_NAME_PATTERN
            .matcher(username)
            .matches()) {

      return Result.fail(
              ResultCode.USER_NAME_FORMAT_ERROR);
    }

    if (userDao.selectByUsername(
            username) != null) {

      return Result.fail(
              ResultCode.USER_NAME_EXISTS);
    }

    return Result.success();
  }

  /**
   * 校验手机号。
   *
   * @param phone 手机号
   * @return 校验结果
   */
  private Result<Void> userPhoneCheck(
          String phone) {

    if (!StringUtils.hasText(phone)
            || !USER_PHONE_PATTERN
            .matcher(phone)
            .matches()) {

      /*
       * 原代码错误地返回了 USER_NAME_FORMAT_ERROR。
       * 若 ResultCode 中已有 USER_PHONE_FORMAT_ERROR，
       * 可替换为对应枚举。
       */
      return Result.buildParamIllegal(
              "手机号格式不正确");
    }

    if (userDao.selectByUserPhone(
            phone) != null) {

      return Result.fail(
              ResultCode.USER_PHONE_EXIST);
    }

    return Result.success();
  }

  /**
   * 校验邮箱。
   *
   * @param email 邮箱
   * @return 校验结果
   */
  private Result<Void> userMailCheck(
          String email) {

    if (!StringUtils.hasText(email)
            || !USER_MAIL_PATTERN
            .matcher(email)
            .matches()) {

      return Result.fail(
              ResultCode.USER_EMAIL_FORMAT_ERROR);
    }

    if (userDao.selectByUserMail(
            email) != null) {

      return Result.fail(
              ResultCode.USER_EMAIL_EXIST);
    }

    return Result.success();
  }
}