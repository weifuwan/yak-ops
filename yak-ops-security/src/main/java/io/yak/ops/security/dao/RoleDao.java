package io.yak.ops.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.security.common.dto.role.RoleQueryDTO;
import io.yak.ops.security.common.entity.role.Role;
import io.yak.ops.security.common.entity.role.RoleBrief;

import java.util.List;

/**
 * 角色数据访问接口。
 */
public interface RoleDao {
    Role selectByRoleName(String var1);

    Role selectByRoleId(Long roleId);

    IPage<Role> selectPage(RoleQueryDTO var1);

    void insert(Role var1);

    void deleteByRoleId(Long roleId);

    void update(Role var1);

    List<RoleBrief>
    selectBriefListByRoleNameAndDescOrderByCreateTime(String var1);

    List<RoleBrief> selectAllBrief();

    List<RoleBrief> selectBriefListByRoleIdList(List<Long> var1);

    int selectCountByRoleNameAndNotRoleId(String roleName, Long roleId);
}
