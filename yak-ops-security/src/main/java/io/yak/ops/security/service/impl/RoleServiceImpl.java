package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.security.common.dto.message.MessageDTO;
import io.yak.ops.security.common.dto.oplog.OplogDTO;
import io.yak.ops.security.common.dto.role.RoleAssignDTO;
import io.yak.ops.security.common.dto.role.RoleQueryDTO;
import io.yak.ops.security.common.dto.role.RoleSaveDTO;
import io.yak.ops.security.common.entity.BaseEntity;
import io.yak.ops.security.common.entity.UserRole;
import io.yak.ops.security.common.entity.role.Role;
import io.yak.ops.security.common.entity.role.RoleBrief;
import io.yak.ops.security.common.entity.user.UserBrief;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.enums.message.MessageCode;
import io.yak.ops.security.common.vo.permission.PermissionTreeVO;
import io.yak.ops.security.common.vo.role.AssignInfoVO;
import io.yak.ops.security.common.vo.role.RoleBriefVO;
import io.yak.ops.security.common.vo.role.RoleDeleteCheckVO;
import io.yak.ops.security.common.vo.role.RoleVO;
import io.yak.ops.security.common.vo.user.UserBasicVO;
import io.yak.ops.security.common.vo.user.UserBriefVO;
import io.yak.ops.security.dao.RoleDao;
import io.yak.ops.security.dao.UserDao;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.service.MessageService;
import io.yak.ops.security.service.OplogService;
import io.yak.ops.security.service.PermissionService;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.service.RoleService;
import io.yak.ops.security.service.UserRoleService;
import io.yak.ops.security.util.CopyBeanUtil;
import io.yak.ops.security.util.JsonUtils;
import io.yak.ops.security.util.MathUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 角色服务实现类。
 *
 * <p>负责角色信息、角色权限关系、用户角色关系、
 * 通知消息以及操作日志的统一编排。
 *
 * @author weifuwan
 */
@Service("yakSecurityRoleServiceImpl")
public class RoleServiceImpl implements RoleService {

  private static final String ROLE_CODE_PREFIX = "r";

  private static final int ROLE_CODE_RANDOM_LENGTH = 7;

  private static final String OPERATION_CREATE = "新增";

  private static final String OPERATION_EDIT = "编辑";

  private static final String OPERATION_DELETE = "删除";

  private static final String OPERATION_OBJECT_ROLE = "Role";

  private static final DateTimeFormatter
          MESSAGE_TIME_FORMATTER =
          DateTimeFormatter.ofPattern("MM-dd HH:mm");

  private final RoleDao roleDao;

  private final PermissionService permissionService;

  private final MessageService messageService;

  private final OplogService oplogService;

  private final UserDao userDao;

  private final RolePermissionService rolePermissionService;

  private final UserRoleService userRoleService;

  /**
   * 创建角色服务。
   *
   * @param roleDao 角色数据访问对象
   * @param permissionService 权限服务
   * @param messageService 消息服务
   * @param oplogService 操作日志服务
   * @param userDao 用户数据访问对象
   * @param rolePermissionService 角色权限服务
   * @param userRoleService 用户角色服务
   */
  public RoleServiceImpl(
          RoleDao roleDao,
          PermissionService permissionService,
          MessageService messageService,
          OplogService oplogService,
          UserDao userDao,
          RolePermissionService rolePermissionService,
          UserRoleService userRoleService) {

    this.roleDao = roleDao;
    this.permissionService = permissionService;
    this.messageService = messageService;
    this.oplogService = oplogService;
    this.userDao = userDao;
    this.rolePermissionService = rolePermissionService;
    this.userRoleService = userRoleService;
  }

  /**
   * 根据角色 ID 查询角色简要信息。
   *
   * @param roleId 角色 ID
   * @return 角色简要信息
   */
  @Override
  public RoleBriefVO getRoleBriefByRoleId(
          Long roleId) {

    if (roleId == null) {
      return null;
    }

    Role role =
            roleDao.selectByRoleId(roleId);

    return CopyBeanUtil.copy(
            role,
            RoleBriefVO.class);
  }

  /**
   * 根据角色 ID 查询角色详情。
   *
   * @param roleId 角色 ID
   * @return 角色详情
   */
  @Override
  public RoleVO getRoleDetailByRoleId(
          Long roleId) {

    if (roleId == null) {
      return null;
    }

    Role role =
            roleDao.selectByRoleId(roleId);

    if (role == null) {
      return null;
    }

    RoleVO roleVO =
            CopyBeanUtil.copy(
                    role,
                    RoleVO.class);

    if (roleVO == null) {
      throw new IllegalStateException(
              "角色对象转换失败");
    }

    PermissionTreeVO permissionTree =
            permissionService
                    .buildPermissionTreeByRoleId(
                            roleId);

    roleVO.setPermissionTreeVO(
            permissionTree);

    roleVO.setCreateTime(
            role.getCreateTime());

    roleVO.setUpdateTime(
            role.getUpdateTime());

    List<Long> userIdList =
            userRoleService
                    .getUserIdListByRoleId(
                            roleId);

    List<UserBriefVO> userList =
            getUserBriefListByUserIds(
                    userIdList);

    List<String> userNameList =
            CollectionUtils.isEmpty(userList)
                    ? new ArrayList<>()
                    : userList.stream()
                    .map(UserBriefVO::getUserName)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

    roleVO.setAuthedUserCnt(
            userIdList.size());

    roleVO.setAuthedUsers(
            userNameList);

    return roleVO;
  }

  /**
   * 分页查询角色。
   *
   * @param queryDTO 查询条件
   * @return 角色分页数据
   */
  @Override
  public PagingData<RoleVO> getRolePage(
          RoleQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "角色查询条件不能为空");
    }

    IPage<Role> rolePage =
            roleDao.selectPage(queryDTO);

    List<Role> roleList =
            rolePage.getRecords();

    if (CollectionUtils.isEmpty(roleList)) {
      return toPagingData(
              new ArrayList<>(),
              rolePage);
    }

    List<Long> roleIdList =
            roleList.stream()
                    .map(BaseEntity::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

    List<UserRole> userRoleList =
            userRoleService.getByRoleIds(
                    roleIdList);

    if (userRoleList == null) {
      userRoleList = new ArrayList<>();
    }

    List<Long> userIdList =
            userRoleList.stream()
                    .map(UserRole::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

    List<UserBasicVO> userList =
            getUserBasicListByUserIds(
                    userIdList);

    Map<Long, String> userNameMap =
            buildUserNameMap(userList);

    Map<Long, List<Long>> roleUserIdMap =
            new HashMap<>();

    Map<Long, List<String>> roleUserNameMap =
            new HashMap<>();

    for (UserRole userRole : userRoleList) {
      if (userRole == null
              || userRole.getRoleId() == null
              || userRole.getUserId() == null) {

        continue;
      }

      roleUserIdMap
              .computeIfAbsent(
                      userRole.getRoleId(),
                      key -> new ArrayList<>())
              .add(userRole.getUserId());

      String userName =
              userNameMap.get(
                      userRole.getUserId());

      if (StringUtils.hasText(userName)) {
        roleUserNameMap
                .computeIfAbsent(
                        userRole.getRoleId(),
                        key -> new ArrayList<>())
                .add(userName);
      }
    }

    List<RoleVO> roleVOList =
            new ArrayList<>(roleList.size());

    for (Role role : roleList) {
      RoleVO roleVO =
              CopyBeanUtil.copy(
                      role,
                      RoleVO.class);

      if (roleVO == null) {
        continue;
      }

      List<Long> assignedUserIds =
              roleUserIdMap.getOrDefault(
                      role.getId(),
                      Collections.emptyList());

      roleVO.setAuthedUserCnt(
              assignedUserIds.size());

      roleVO.setAuthedUsers(
              roleUserNameMap.getOrDefault(
                      role.getId(),
                      Collections.emptyList()));

      roleVO.setCreateTime(
              role.getCreateTime());

      roleVO.setUpdateTime(
              role.getUpdateTime());

      roleVOList.add(roleVO);
    }

    return toPagingData(
            roleVOList,
            rolePage);
  }

  /**
   * 创建角色。
   *
   * @param roleSaveDTO 角色信息
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void createRole(
          RoleSaveDTO roleSaveDTO,
          String operator) {

    checkParam(roleSaveDTO, false);

    Role role =
            CopyBeanUtil.copy(
                    roleSaveDTO,
                    Role.class);

    if (role == null) {
      throw new IllegalStateException(
              "角色对象转换失败");
    }

    role.setRoleCode(
            generateRoleCode());

    setLastReviser(
            role,
            operator);


     roleDao.insert(role);

    rolePermissionService.saveRolePermission(
            role.getId(),
            roleSaveDTO.getPermissionIdList());

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_CREATE,
                    OPERATION_OBJECT_ROLE,
                    roleSaveDTO.getRoleName(),
                    JsonUtils.toJson(
                            roleSaveDTO)));
  }

  /**
   * 根据角色 ID 删除角色。
   *
   * @param roleId 角色 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteRoleByRoleId(
          Long roleId,
          String operator) {

    if (roleId == null) {
      throw new IllegalArgumentException(
              "角色 ID 不能为空");
    }

    Role role =
            roleDao.selectByRoleId(roleId);

    if (role == null) {
      throw new YakSecurityException(
              ResultCode.ROLE_NOT_EXISTS);
    }

    userRoleService.deleteByUserIdOrRoleId(
            null,
            roleId);
    rolePermissionService
            .deleteRolePermissionByRoleId(
                    roleId);

    roleDao.deleteByRoleId(roleId);

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_DELETE,
                    OPERATION_OBJECT_ROLE,
                    role.getRoleName(),
                    JsonUtils.toJson(role)));
  }

  /**
   * 从角色中删除用户。
   *
   * @param roleId 角色 ID
   * @param userId 用户 ID
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deleteUserFromRole(
          Long roleId,
          Long userId,
          String operator) {

    if (roleId == null || userId == null) {
      throw new IllegalArgumentException(
              "角色 ID 和用户 ID 不能为空");
    }

    Role role =
            roleDao.selectByRoleId(roleId);

    if (role == null) {
      throw new YakSecurityException(
              ResultCode.ROLE_NOT_EXISTS);
    }

    userRoleService.deleteByUserIdOrRoleId(
            userId,
            roleId);

    Role updateRole = new Role();
    updateRole.setId(roleId);

    setLastReviser(
            updateRole,
            operator);

    roleDao.update(updateRole);

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_EDIT,
                    OPERATION_OBJECT_ROLE,
                    role.getRoleName(),
                    "从角色中删除用户，userId="
                            + userId));
  }

  /**
   * 更新角色。
   *
   * @param roleSaveDTO 角色信息
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void updateRole(
          RoleSaveDTO roleSaveDTO,
          String operator) {

    if (roleSaveDTO == null
            || roleSaveDTO.getId() == null) {

      throw new IllegalArgumentException(
              "角色信息和角色 ID 不能为空");
    }

    Role currentRole =
            roleDao.selectByRoleId(
                    roleSaveDTO.getId());
    if (currentRole == null) {
      throw new YakSecurityException(
              ResultCode.ROLE_NOT_EXISTS);
    }

    checkParam(roleSaveDTO, true);

    Role role =
            CopyBeanUtil.copy(
                    roleSaveDTO,
                    Role.class);

    if (role == null) {
      throw new IllegalStateException(
              "角色对象转换失败");
    }

    setLastReviser(
            role,
            operator);

    roleDao.update(role);

    rolePermissionService
            .updateRolePermission(
                    role.getId(),
                    roleSaveDTO
                            .getPermissionIdList());

    oplogService.saveOplog(
            new OplogDTO(
                    operator,
                    OPERATION_EDIT,
                    OPERATION_OBJECT_ROLE,
                    roleSaveDTO.getRoleName(),
                    JsonUtils.toJson(
                            roleSaveDTO)));
  }

  /**
   * 分配角色或为角色分配用户。
   *
   * @param assignDTO 分配参数
   * @param operator 操作人
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void assignRoles(
          RoleAssignDTO assignDTO,
          String operator) {

    checkAssignParam(assignDTO);

    List<Long> newIdList =
            normalizeIds(
                    assignDTO.getIdList());

    assignDTO.setIdList(newIdList);

    if (Boolean.TRUE.equals(
            assignDTO.getFlag())) {

      assignRolesToUser(
              assignDTO,
              operator);

      return;
    }

    assignUsersToRole(
            assignDTO,
            operator);
  }

  /**
   * 为用户分配角色。
   */
  private void assignRolesToUser(
          RoleAssignDTO assignDTO,
          String operator) {

    Long userId = assignDTO.getId();

    List<Long> oldRoleIdList =
            userRoleService
                    .getRoleIdListByUserId(
                            userId);

    userRoleService.updateUserRoleByUserId(
            userId,
            assignDTO.getIdList());

    String targetName =
            getUserDisplayName(userId);

    Long oplogId =
            oplogService.saveOplog(
                    new OplogDTO(
                            operator,
                            OPERATION_EDIT,
                            OPERATION_OBJECT_ROLE,
                            targetName,
                            "给用户分配角色，"
                                    + JsonUtils.toJson(
                                    assignDTO)));

    packAndSaveMessage(
            oplogId,
            oldRoleIdList,
            assignDTO);
  }

  /**
   * 为角色分配用户。
   */
  private void assignUsersToRole(
          RoleAssignDTO assignDTO,
          String operator) {

    Long roleId = assignDTO.getId();

    Role role =
            roleDao.selectByRoleId(roleId);

    if (role == null) {
      throw new YakSecurityException(
              ResultCode.ROLE_NOT_EXISTS);
    }

    List<Long> oldUserIdList =
            userRoleService
                    .getUserIdListByRoleId(
                            roleId);

    userRoleService.updateUserRoleByRoleId(
            roleId,
            assignDTO.getIdList());

    Role updateRole = new Role();
    updateRole.setId(roleId);

    setLastReviser(
            updateRole,
            operator);

    roleDao.update(updateRole);

    Long oplogId =
            oplogService.saveOplog(
                    new OplogDTO(
                            operator,
                            OPERATION_EDIT,
                            OPERATION_OBJECT_ROLE,
                            role.getRoleName(),
                            "给角色分配用户，"
                                    + JsonUtils.toJson(
                                    assignDTO)));

    packAndSaveMessage(
            oplogId,
            oldUserIdList,
            assignDTO);
  }

  /**
   * 根据角色名称查询角色简要信息。
   *
   * @param roleName 角色名称
   * @return 角色简要信息列表
   */
  @Override
  public List<RoleBriefVO> getRoleBriefListByRoleName(
          String roleName) {

    List<RoleBrief> roleList =
            roleDao
                    .selectBriefListByRoleNameAndDescOrderByCreateTime(
                            roleName);

    List<RoleBriefVO> result =
            CopyBeanUtil.copyList(
                    roleList,
                    RoleBriefVO.class);

    return result == null
            ? new ArrayList<>()
            : result;
  }

  /**
   * 执行角色删除前校验。
   *
   * @param roleId 角色 ID
   * @return 删除校验结果
   */
  @Override
  public RoleDeleteCheckVO checkBeforeDelete(
          Long roleId) {

    if (roleId == null) {
      return null;
    }

    RoleDeleteCheckVO checkVO =
            new RoleDeleteCheckVO();

    checkVO.setRoleId(roleId);

    List<Long> userIdList =
            userRoleService
                    .getUserIdListByRoleId(
                            roleId);

    if (CollectionUtils.isEmpty(userIdList)) {
      return checkVO;
    }

    List<UserBriefVO> userList =
            getUserBriefListByUserIds(
                    userIdList);

    List<String> userNameList =
            userList.stream()
                    .map(UserBriefVO::getUserName)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

    checkVO.setUserNameList(
            userNameList);

    return checkVO;
  }

  /**
   * 查询全部角色简要信息。
   *
   * @return 角色简要信息列表
   */
  @Override
  public List<RoleBriefVO> getAllRoleBriefList() {
    List<RoleBriefVO> roleList =
            CopyBeanUtil.copyList(
                    roleDao.selectAllBrief(),
                    RoleBriefVO.class);

    return roleList == null
            ? new ArrayList<>()
            : roleList;
  }

  /**
   * 根据用户 ID 查询角色简要信息。
   *
   * @param userId 用户 ID
   * @return 角色简要信息列表
   */
  @Override
  public List<RoleBriefVO> getRoleBriefListByUserId(
          Long userId) {

    if (userId == null) {
      return new ArrayList<>();
    }

    List<Long> roleIdList =
            userRoleService
                    .getRoleIdListByUserId(
                            userId);

    if (CollectionUtils.isEmpty(roleIdList)) {
      return new ArrayList<>();
    }

    List<RoleBriefVO> roleList =
            CopyBeanUtil.copyList(
                    roleDao
                            .selectBriefListByRoleIdList(
                                    roleIdList),
                    RoleBriefVO.class);

    return roleList == null
            ? new ArrayList<>()
            : roleList;
  }

  /**
   * 根据用户 ID 集合查询角色简要信息。
   *
   * @param userIdList 用户 ID 列表
   * @return 用户与角色列表映射
   */
  @Override
  public Map<Long, List<RoleBriefVO>>
  getRoleBriefListByUserIds(
          List<Long> userIdList) {

    List<Long> validUserIds =
            normalizeIds(userIdList);

    if (validUserIds.isEmpty()) {
      return new HashMap<>();
    }

    List<UserRole> userRoleList =
            userRoleService
                    .getRoleIdListByUserIds(
                            validUserIds);

    if (CollectionUtils.isEmpty(userRoleList)) {
      return new HashMap<>();
    }

    List<Long> roleIdList =
            userRoleList.stream()
                    .map(UserRole::getRoleId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

    List<RoleBriefVO> roleList =
            CopyBeanUtil.copyList(
                    roleDao
                            .selectBriefListByRoleIdList(
                                    roleIdList),
                    RoleBriefVO.class);

    Map<Long, RoleBriefVO> roleMap =
            new HashMap<>();

    if (!CollectionUtils.isEmpty(roleList)) {
      for (RoleBriefVO role : roleList) {
        if (role != null
                && role.getId() != null) {

          roleMap.put(
                  role.getId(),
                  role);
        }
      }
    }

    Map<Long, List<RoleBriefVO>> result =
            new HashMap<>();

    for (UserRole userRole : userRoleList) {
      if (userRole == null
              || userRole.getUserId() == null
              || userRole.getRoleId() == null) {

        continue;
      }

      RoleBriefVO role =
              roleMap.get(
                      userRole.getRoleId());

      if (role == null) {
        continue;
      }

      result.computeIfAbsent(
              userRole.getUserId(),
              key -> new ArrayList<>())
              .add(role);
    }

    return result;
  }

  /**
   * 根据角色 ID 查询用户分配信息。
   *
   * @param roleId 角色 ID
   * @return 用户分配信息列表
   */
  @Override
  public List<AssignInfoVO> getAssignInfoByRoleId(
          Long roleId) {

    if (roleId == null) {
      return new ArrayList<>();
    }

    List<UserBriefVO> userList =
            getAllUserBriefList();

    if (CollectionUtils.isEmpty(userList)) {
      return new ArrayList<>();
    }

    Set<Long> assignedUserIds =
            new HashSet<>(
                    userRoleService
                            .getUserIdListByRoleId(
                                    roleId));

    List<AssignInfoVO> resultList =
            new ArrayList<>(userList.size());

    for (UserBriefVO user : userList) {
      AssignInfoVO assignInfo =
              new AssignInfoVO();

      assignInfo.setId(user.getId());
      assignInfo.setName(
              user.getUserName());

      assignInfo.setHas(
              assignedUserIds.contains(
                      user.getId()));

      resultList.add(assignInfo);
    }

    return resultList;
  }

  /**
   * 保存角色分配变更消息。
   */
  private void packAndSaveMessage(
          Long oplogId,
          List<Long> oldIdList,
          RoleAssignDTO assignDTO) {

    List<Long> oldIds =
            normalizeIds(oldIdList);

    List<Long> newIds =
            normalizeIds(
                    assignDTO.getIdList());

    Set<Long> oldIdSet =
            new HashSet<>(oldIds);

    Set<Long> newIdSet =
            new HashSet<>(newIds);

    List<Long> removeIdList =
            oldIds.stream()
                    .filter(id ->
                            !newIdSet.contains(id))
                    .collect(Collectors.toList());

    List<Long> addIdList =
            newIds.stream()
                    .filter(id ->
                            !oldIdSet.contains(id))
                    .collect(Collectors.toList());

    if (Boolean.TRUE.equals(
            assignDTO.getFlag())) {

      List<Long> userIds =
              Collections.singletonList(
                      assignDTO.getId());

      saveRoleAssignMessage(
              oplogId,
              userIds,
              removeIdList,
              userIds,
              addIdList);

      return;
    }

    List<Long> roleIds =
            Collections.singletonList(
                    assignDTO.getId());

    saveRoleAssignMessage(
            oplogId,
            removeIdList,
            roleIds,
            addIdList,
            roleIds);
  }

  /**
   * 保存角色新增或移除通知。
   */
  private void saveRoleAssignMessage(
          Long oplogId,
          List<Long> removeUserIdList,
          List<Long> removeRoleIdList,
          List<Long> addUserIdList,
          List<Long> addRoleIdList) {

    List<MessageDTO> messageList =
            new ArrayList<>();

    String time =
            LocalDateTime.now()
                    .format(
                            MESSAGE_TIME_FORMATTER);

    if (!CollectionUtils.isEmpty(addUserIdList)
            && !CollectionUtils.isEmpty(
            addRoleIdList)) {

      String addRoleInfo =
              spliceRoleNameByRoleIdList(
                      addRoleIdList);

      if (StringUtils.hasText(addRoleInfo)) {
        for (Long userId : addUserIdList) {
          if (userId == null) {
            continue;
          }

          MessageDTO message =
                  new MessageDTO(
                          userId,
                          oplogId);

          message.setContent(
                  String.format(
                          MessageCode
                                  .ROLE_ADD_MESSAGE
                                  .getContent(),
                          time,
                          addRoleInfo));

          message.setTitle(
                  MessageCode
                          .ROLE_ADD_MESSAGE
                          .getTitle());

          messageList.add(message);
        }
      }
    }

    if (!CollectionUtils.isEmpty(
            removeUserIdList)
            && !CollectionUtils.isEmpty(
            removeRoleIdList)) {

      String removeRoleInfo =
              spliceRoleNameByRoleIdList(
                      removeRoleIdList);

      if (StringUtils.hasText(
              removeRoleInfo)) {

        for (Long userId : removeUserIdList) {
          if (userId == null) {
            continue;
          }

          MessageDTO message =
                  new MessageDTO(
                          userId,
                          oplogId);

          message.setContent(
                  String.format(
                          MessageCode
                                  .ROLE_REMOVE_MESSAGE
                                  .getContent(),
                          time,
                          removeRoleInfo));

          message.setTitle(
                  MessageCode
                          .ROLE_REMOVE_MESSAGE
                          .getTitle());

          messageList.add(message);
        }
      }
    }

    if (!messageList.isEmpty()) {
      messageService.saveMessages(
              messageList);
    }
  }

  /**
   * 根据角色 ID 列表拼接角色名称。
   */
  private String spliceRoleNameByRoleIdList(
          List<Long> roleIdList) {

    List<Long> validRoleIds =
            normalizeIds(roleIdList);

    if (validRoleIds.isEmpty()) {
      return null;
    }

    List<RoleBrief> roleList =
            roleDao.selectBriefListByRoleIdList(
                    validRoleIds);

    if (CollectionUtils.isEmpty(roleList)) {
      return null;
    }

    return roleList.stream()
            .map(RoleBrief::getRoleName)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining(","));
  }

  /**
   * 校验角色保存参数。
   */
  private void checkParam(
          RoleSaveDTO roleSaveDTO,
          boolean update) {

    if (roleSaveDTO == null) {
      throw new IllegalArgumentException(
              "角色信息不能为空");
    }

    if (!StringUtils.hasText(
            roleSaveDTO.getRoleName())) {

      throw new YakSecurityException(
              ResultCode
                      .ROLE_NAME_CANNOT_BE_BLANK);
    }

    if (CollectionUtils.isEmpty(
            roleSaveDTO.getPermissionIdList())) {

      throw new YakSecurityException(
              ResultCode
                      .ROLE_PERMISSION_CANNOT_BE_NULL);
    }

    Long excludeRoleId = null;

    if (update) {
      if (roleSaveDTO.getId() == null) {
        throw new IllegalArgumentException(
                "角色 ID 不能为空");
      }

      excludeRoleId =
              roleSaveDTO.getId();
    }

    int duplicateCount =
            roleDao
                    .selectCountByRoleNameAndNotRoleId(
                            roleSaveDTO.getRoleName(),
                            excludeRoleId);

    if (duplicateCount > 0) {
      throw new YakSecurityException(
              ResultCode
                      .ROLE_NAME_ALREADY_EXISTS);
    }
  }

  /**
   * 校验角色分配参数。
   */
  private void checkAssignParam(
          RoleAssignDTO assignDTO) {

    if (assignDTO == null) {
      throw new IllegalArgumentException(
              "角色分配参数不能为空");
    }

    if (assignDTO.getFlag() == null) {
      throw new YakSecurityException(
              ResultCode
                      .ROLE_ASSIGN_FLAG_IS_NULL);
    }

    if (assignDTO.getId() == null) {
      throw new IllegalArgumentException(
              "角色分配目标 ID 不能为空");
    }
  }

  /**
   * 根据用户 ID 集合查询用户基础信息。
   */
  private List<UserBasicVO> getUserBasicListByUserIds(
          List<Long> userIds) {

    if (CollectionUtils.isEmpty(userIds)) {
      return new ArrayList<>();
    }

    List<UserBasicVO> users =
            CopyBeanUtil.copyList(
                    userDao.selectBriefListByUserIdList(
                            userIds),
                    UserBasicVO.class);

    return users == null
            ? new ArrayList<>()
            : users;
  }

  /**
   * 根据用户 ID 集合查询用户简要信息。
   */
  private List<UserBriefVO> getUserBriefListByUserIds(
          List<Long> userIds) {

    if (CollectionUtils.isEmpty(userIds)) {
      return new ArrayList<>();
    }

    List<UserBriefVO> users =
            CopyBeanUtil.copyList(
                    userDao.selectBriefListByUserIdList(
                            userIds),
                    UserBriefVO.class);

    return users == null
            ? new ArrayList<>()
            : users;
  }

  /**
   * 查询全部用户简要信息。
   */
  private List<UserBriefVO> getAllUserBriefList() {
    List<UserBrief> users =
            userDao.selectAllBriefList();

    List<UserBriefVO> result =
            CopyBeanUtil.copyList(
                    users,
                    UserBriefVO.class);

    return result == null
            ? new ArrayList<>()
            : result;
  }

  /**
   * 构建用户 ID 与用户名映射。
   */
  private Map<Long, String> buildUserNameMap(
          List<UserBasicVO> userList) {

    if (CollectionUtils.isEmpty(userList)) {
      return new HashMap<>();
    }

    Map<Long, String> result =
            new HashMap<>();

    for (UserBasicVO user : userList) {
      if (user == null
              || user.getId() == null) {

        continue;
      }

      result.put(
              user.getId(),
              user.getUserName());
    }

    return result;
  }

  /**
   * 获取用户显示名称。
   */
  private String getUserDisplayName(
          Long userId) {

    List<UserBriefVO> userList =
            getUserBriefListByUserIds(
                    Collections.singletonList(
                            userId));

    if (CollectionUtils.isEmpty(userList)) {
      return String.valueOf(userId);
    }

    UserBriefVO user = userList.get(0);

    if (StringUtils.hasText(
            user.getRealName())) {

      return user.getRealName();
    }

    if (StringUtils.hasText(
            user.getUserName())) {

      return user.getUserName();
    }

    return String.valueOf(userId);
  }

  /**
   * 设置最后修改人。
   */
  private void setLastReviser(
          Role role,
          String operator) {

    if (StringUtils.hasText(operator)) {
      role.setLastReviser(operator);
    }
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
   * 生成角色编码。
   */
  private String generateRoleCode() {
    return ROLE_CODE_PREFIX
            + MathUtil.getRandomNumber(
            ROLE_CODE_RANDOM_LENGTH);
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