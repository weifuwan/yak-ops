package io.yak.ops.security.service.impl;

import io.yak.ops.security.common.dto.permission.PermissionDTO;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.vo.permission.PermissionTreeVO;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.service.PermissionService;
import io.yak.ops.security.service.PermissionCache;
import io.yak.ops.security.service.RolePermissionService;
import io.yak.ops.security.util.CopyBeanUtil;
import io.yak.ops.security.util.MathUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 权限服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityPermissionServiceImpl")
public class PermissionServiceImpl
        implements PermissionService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(
                  PermissionServiceImpl.class);

  /**
   * 虚拟根权限 ID。
   */
  private static final Long ROOT_PERMISSION_ID = 0L;

  /**
   * 权限 ID 随机部分长度。
   */
  private static final int PERMISSION_ID_RANDOM_LENGTH = 5;

  /**
   * 权限 ID 时间部分放大倍数。
   */
  private static final long PERMISSION_ID_FACTOR = 100_000L;

  /**
   * 权限 ID 最大生成重试次数。
   */
  private static final int MAX_ID_RETRY_COUNT = 100;

  private final PermissionDao permissionDao;

  private final RolePermissionService rolePermissionService;
  private final PermissionCache permissionCache;

  /**
   * 创建权限服务。
   *
   * @param permissionDao 权限数据访问对象
   * @param rolePermissionService 角色权限关系服务
   */
  public PermissionServiceImpl(
          PermissionDao permissionDao,
          RolePermissionService rolePermissionService,
          PermissionCache permissionCache) {

    this.permissionDao = permissionDao;
    this.rolePermissionService = rolePermissionService;
    this.permissionCache = permissionCache;
  }

  /**
   * 构建包含选中状态的权限树。
   *
   * @param permissionIdList 已选中的权限 ID 列表
   * @return 权限树
   */
  @Override
  public PermissionTreeVO buildPermissionTreeWithHas(
          List<Long> permissionIdList) {

    Set<Long> selectedPermissionIds =
            normalizeIds(permissionIdList)
                    .stream()
                    .collect(Collectors.toSet());

    return buildPermissionTree(
            selectedPermissionIds);
  }

  /**
   * 构建权限树。
   *
   * @return 权限树
   */
  @Override
  public PermissionTreeVO buildPermissionTree() {
    return buildPermissionTree(
            Collections.emptySet());
  }

  /**
   * 根据角色 ID 构建包含选中状态的权限树。
   *
   * @param roleId 角色 ID
   * @return 权限树
   */
  @Override
  public PermissionTreeVO buildPermissionTreeByRoleId(
          Long roleId) {

    if (roleId == null) {
      return buildPermissionTree();
    }

    List<Long> permissionIdList =
            rolePermissionService
                    .getPermissionIdListByRoleId(
                            roleId);

    return buildPermissionTreeWithHas(
            permissionIdList);
  }

  /**
   * 批量保存权限树。
   *
   * @param permissionDTOList 权限树数据
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void savePermission(
          List<PermissionDTO> permissionDTOList) {

    if (CollectionUtils.isEmpty(
            permissionDTOList)) {

      return;
    }

    List<Permission> permissionList =
            buildPermissionList(
                    permissionDTOList);

    if (permissionList.isEmpty()) {
      return;
    }

    permissionDao.insertBatch(
            permissionList);
    permissionCache.invalidateAll();

    LOGGER.info(
            "批量导入权限成功，权限数量={}",
            permissionList.size());
  }

  /** 根据权限 ID 删除权限及其角色关联。 */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void deletePermissionById(Long permissionId) {
    if (permissionId == null) {
      throw new IllegalArgumentException(
              "权限 ID 不能为空");
    }

    rolePermissionService
            .deleteRolePermissionByPermissionId(
                    permissionId);
    permissionDao.deleteById(permissionId);
    permissionCache.invalidateAll();
  }

  /**
   * 构建权限树。
   *
   * @param selectedPermissionIds 已选中的权限 ID 集合
   * @return 权限树
   */
  private PermissionTreeVO buildPermissionTree(
          Set<Long> selectedPermissionIds) {

    List<Permission> permissionList =
            permissionDao
                    .selectAllAndAscOrderByLevel();

    PermissionTreeVO root =
            PermissionTreeVO.builder()
                    .id(ROOT_PERMISSION_ID)
                    .leaf(Boolean.FALSE)
                    .has(Boolean.TRUE)
                    .childList(new ArrayList<>())
                    .build();

    if (CollectionUtils.isEmpty(permissionList)) {
      return root;
    }

    Map<Long, PermissionTreeVO> permissionTreeMap =
            new HashMap<>(
                    permissionList.size() + 1);

    permissionTreeMap.put(
            ROOT_PERMISSION_ID,
            root);

    for (Permission permission : permissionList) {
      if (permission == null
              || permission.getId() == null) {

        LOGGER.error(
                "权限数据异常，权限或权限 ID 为空");

        throw new YakSecurityException(
                ResultCode.PERMISSION_DATA_ERROR);
      }

      PermissionTreeVO permissionTreeVO =
              CopyBeanUtil.copy(
                      permission,
                      PermissionTreeVO.class);

      if (permissionTreeVO == null) {
        throw new IllegalStateException(
                "权限树对象转换失败");
      }

      if (!Boolean.TRUE.equals(
              permissionTreeVO.getLeaf())) {

        permissionTreeVO.setChildList(
                new ArrayList<>());
      }

      Long parentId =
              permission.getParentId() == null
                      ? ROOT_PERMISSION_ID
                      : permission.getParentId();

      PermissionTreeVO parent =
              permissionTreeMap.get(parentId);

      if (parent == null) {
        LOGGER.error(
                "构建权限树失败，未找到父权限，"
                        + "权限ID={}，父权限ID={}",
                permission.getId(),
                parentId);

        throw new YakSecurityException(
                ResultCode.PERMISSION_DATA_ERROR);
      }

      /*
       * 保留原有逻辑：
       * 当前权限选中，并且父权限也处于选中状态时，
       * 当前节点才标记为选中。
       */
      boolean selected =
              Boolean.TRUE.equals(parent.getHas())
                      && selectedPermissionIds.contains(
                      permission.getId());

      permissionTreeVO.setHas(selected);

      /*
       * 若数据库中的父节点错误地被标记为叶子节点，
       * 这里仍保证树可以正常构建。
       */
      if (parent.getChildList() == null) {
        parent.setChildList(
                new ArrayList<>());
      }

      parent.setLeaf(Boolean.FALSE);
      parent.getChildList().add(
              permissionTreeVO);

      PermissionTreeVO previous =
              permissionTreeMap.put(
                      permissionTreeVO.getId(),
                      permissionTreeVO);

      if (previous != null) {
        LOGGER.error(
                "构建权限树失败，存在重复权限ID，"
                        + "权限ID={}",
                permissionTreeVO.getId());

        throw new YakSecurityException(
                ResultCode.PERMISSION_DATA_ERROR);
      }
    }

    return root;
  }

  /**
   * 将权限 DTO 树转换为权限实体列表。
   *
   * @param permissionDTOList 权限 DTO 列表
   * @return 权限实体列表
   */
  private List<Permission> buildPermissionList(
          List<PermissionDTO> permissionDTOList) {

    List<Permission> permissionList =
            new ArrayList<>();

    Deque<PermissionQueueNode> queue =
            new ArrayDeque<>();

    /*
     * 使用对象地址判断 DTO 是否被重复处理，
     * 防止输入树存在循环引用。
     */
    Set<PermissionDTO> visitedDTOs =
            Collections.newSetFromMap(
                    new IdentityHashMap<>());

    Set<Long> generatedPermissionIds =
            new HashSet<>();

    for (PermissionDTO permissionDTO
            : permissionDTOList) {

      if (permissionDTO == null) {
        throw new YakSecurityException(
                ResultCode.PERMISSION_DATA_ERROR);
      }

      queue.offer(
              new PermissionQueueNode(
                      permissionDTO,
                      ROOT_PERMISSION_ID,
                      1));
    }

    while (!queue.isEmpty()) {
      PermissionQueueNode queueNode =
              queue.poll();

      PermissionDTO permissionDTO =
              queueNode.getPermissionDTO();

      if (!visitedDTOs.add(permissionDTO)) {
        LOGGER.error(
                "导入权限数据存在循环引用");

        throw new YakSecurityException(
                ResultCode.PERMISSION_DATA_ERROR);
      }

      Permission permission =
              CopyBeanUtil.copy(
                      permissionDTO,
                      Permission.class);

      if (permission == null) {
        throw new IllegalStateException(
                "权限对象转换失败");
      }

      long permissionId =
              generateUniquePermissionId(
                      generatedPermissionIds);

      List<PermissionDTO> childPermissionList =
              permissionDTO
                      .getChildPermissionDTOList();

      permission.setId(permissionId);
      permission.setParentId(
              queueNode.getParentId());
      permission.setLevel(
              queueNode.getLevel());
      permission.setLeaf(
              CollectionUtils.isEmpty(
                      childPermissionList));

      permissionList.add(permission);

      if (CollectionUtils.isEmpty(
              childPermissionList)) {

        continue;
      }

      for (PermissionDTO childPermission
              : childPermissionList) {

        if (childPermission == null) {
          throw new YakSecurityException(
                  ResultCode.PERMISSION_DATA_ERROR);
        }

        queue.offer(
                new PermissionQueueNode(
                        childPermission,
                        permissionId,
                        queueNode.getLevel() + 1));
      }
    }

    return permissionList;
  }

  /**
   * 生成当前批次内唯一的权限 ID。
   *
   * @param generatedPermissionIds 已生成的权限 ID
   * @return 权限 ID
   */
  private long generateUniquePermissionId(
          Set<Long> generatedPermissionIds) {

    for (int index = 0;
         index < MAX_ID_RETRY_COUNT;
         index++) {

      long permissionId =
              getPermissionId();

      if (!Objects.equals(
              ROOT_PERMISSION_ID,
              permissionId)
              && generatedPermissionIds.add(
              permissionId)) {

        return permissionId;
      }
    }

    throw new IllegalStateException(
            "生成权限 ID 失败");
  }

  /**
   * 生成权限 ID。
   *
   * <p>保留原有 ID 生成规则，避免改变已有权限 ID 格式。
   *
   * @return 权限 ID
   */
  private long getPermissionId() {
    return System.currentTimeMillis() % 1_000L
            * PERMISSION_ID_FACTOR
            + MathUtil.getRandomNumber(
            PERMISSION_ID_RANDOM_LENGTH);
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

  /**
   * 权限树遍历队列节点。
   */
  private static final class PermissionQueueNode {

    private final PermissionDTO permissionDTO;

    private final Long parentId;

    private final int level;

    private PermissionQueueNode(
            PermissionDTO permissionDTO,
            Long parentId,
            int level) {

      this.permissionDTO = permissionDTO;
      this.parentId = parentId;
      this.level = level;
    }

    private PermissionDTO getPermissionDTO() {
      return permissionDTO;
    }

    private Long getParentId() {
      return parentId;
    }

    private int getLevel() {
      return level;
    }
  }
}
