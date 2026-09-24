package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.entity.Permission;
import io.yak.ops.security.common.po.PermissionPO;
import io.yak.ops.security.dao.PermissionDao;
import io.yak.ops.security.dao.mapper.PermissionMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 权限数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class PermissionDaoImpl implements PermissionDao {

  private final PermissionMapper permissionMapper;

  /** 查询全部权限，并按照权限层级升序排列。 */
  @Override
  public List<Permission> selectAllAndAscOrderByLevel() {
    List<PermissionPO> permissionPOList =
        permissionMapper.selectList(
            Wrappers.<PermissionPO>lambdaQuery()
                .orderByAsc(PermissionPO::getLevel)
                .orderByAsc(PermissionPO::getId));

    return CopyBeanUtil.copyList(permissionPOList, Permission.class);
  }

  /** 批量新增权限。 */
  @Override
  public void insertBatch(List<Permission> permissionList) {
    if (permissionList == null || permissionList.isEmpty()) {
      return;
    }

    CopyBeanUtil.copyList(permissionList, PermissionPO.class)
        .forEach(permissionMapper::insert);
  }

  /** 根据权限标识删除权限。 */
  @Override
  public int deleteById(Long permissionId) {
    if (permissionId == null) {
      return 0;
    }
    return permissionMapper.deleteById(permissionId);
  }

  @Override
  public void synchronizeDeclared(List<Permission> permissions) {
    List<PermissionPO> existing = permissionMapper.selectList(
        Wrappers.<PermissionPO>lambdaQuery());
    java.util.Map<String, PermissionPO> byCode = new java.util.HashMap<>();
    existing.forEach(item -> byCode.put(item.getPermissionCode(), item));
    java.util.Set<String> desiredCodes = new java.util.HashSet<>();

    // Groups first so generated database identifiers are available to leaves.
    permissions.stream()
        .sorted(java.util.Comparator.comparing(Permission::getLevel))
        .forEach(item -> {
          desiredCodes.add(item.getPermissionCode());
          PermissionPO row = byCode.get(item.getPermissionCode());
          if (row == null) {
            row = CopyBeanUtil.copy(item, PermissionPO.class);
            permissionMapper.insert(row);
            byCode.put(row.getPermissionCode(), row);
          } else {
            row.setPermissionName(item.getPermissionName());
            row.setDescription(item.getDescription());
            row.setLeaf(item.getLeaf());
            row.setLevel(item.getLevel());
            // 未声明 menuCode 时保留 Flyway 或宿主应用维护的菜单绑定。
            if (StringUtils.hasText(item.getMenuCode())) {
              row.setMenuCode(item.getMenuCode());
            }
            row.setActive(true);
          }
          if (item.getParentCode() != null) {
            PermissionPO parent = byCode.get(item.getParentCode());
            if (parent == null) {
              throw new IllegalStateException(
                  "Missing permission group: " + item.getParentCode());
            }
            row.setParentId(parent.getId());
          } else {
            row.setParentId(0L);
          }
          row.setDeclared(true);
          permissionMapper.updateById(row);
        });
    existing.stream()
        .filter(item -> Boolean.TRUE.equals(item.getDeclared()))
        .filter(item -> !desiredCodes.contains(item.getPermissionCode()))
        .filter(item -> Boolean.TRUE.equals(item.getActive()))
        .forEach(item -> {
          item.setActive(false);
          permissionMapper.updateById(item);
        });
  }
}
