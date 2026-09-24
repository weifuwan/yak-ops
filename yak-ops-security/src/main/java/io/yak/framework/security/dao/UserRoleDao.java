package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.UserRole;
import io.yak.framework.security.common.po.UserRolePO;

import java.util.List;

/**
 * 用户角色关系数据访问接口。
 */
public interface UserRoleDao {
    List<Long> selectUserIdListByRoleId(Long roleId);

    List<Long> selectRoleIdListByUserId(Long userId);

    void insertBatch(List<UserRole> var1);

    int deleteByUserIdOrRoleId(Long userId, Long roleId);

    int selectCountByRoleId(Long roleId);

    List<UserRolePO> selectByRoleIds(List<Long> var1);

    List<UserRolePO> getRoleIdListByUserIds(List<Long> var1);
}
