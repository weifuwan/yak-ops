package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.security.common.dto.role.RoleQueryDTO;
import io.yak.ops.security.common.entity.role.Role;
import io.yak.ops.security.common.entity.role.RoleBrief;
import io.yak.ops.security.common.po.RolePO;
import io.yak.ops.security.dao.RoleDao;
import io.yak.ops.security.dao.mapper.RoleMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class RoleDaoImpl
        implements RoleDao {

    private final RoleMapper roleMapper;

    /**
     * 根据角色名称查询角色。
     *
     * @param roleName 角色名称
     * @return 角色信息
     */
    @Override
    public Role selectByRoleName(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return null;
        }

        RolePO rolePO = roleMapper.selectOne(
                Wrappers.<RolePO>lambdaQuery()
                        .eq(RolePO::getRoleName, roleName)
        );

        return CopyBeanUtil.copy(rolePO, Role.class);
    }

    /**
     * 根据角色主键查询角色。
     *
     * @param roleId 角色主键
     * @return 角色信息
     */
    @Override
    public Role selectByRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                roleMapper.selectById(roleId),
                Role.class
        );
    }

    /**
     * 分页查询角色。
     *
     * <p>角色编码存在时使用精确查询，否则根据角色名称和描述进行模糊查询。</p>
     *
     * @param queryDTO 查询条件
     * @return 角色分页数据
     */
    @Override
    public IPage<Role> selectPage(RoleQueryDTO queryDTO) {
        Page<RolePO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        LambdaQueryWrapper<RolePO> wrapper =
                Wrappers.<RolePO>lambdaQuery()
                        .eq(
                                queryDTO.getId() != null,
                                RolePO::getId,
                                queryDTO.getId()
                        );

        if (StringUtils.hasText(queryDTO.getRoleCode())) {
            wrapper.eq(
                    RolePO::getRoleCode,
                    queryDTO.getRoleCode()
            );
        } else {
            wrapper.like(
                    StringUtils.hasText(queryDTO.getRoleName()),
                    RolePO::getRoleName,
                    queryDTO.getRoleName()
            )
                    .like(
                            StringUtils.hasText(queryDTO.getDescription()),
                            RolePO::getDescription,
                            queryDTO.getDescription()
                    );
        }

        wrapper.orderByDesc(RolePO::getCreateTime);

        IPage<RolePO> result =
                roleMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(result, Role.class);
    }

    /**
     * 新增角色，并回填角色主键。
     *
     * @param role 角色信息
     */
    @Override
    public void insert(Role role) {
        RolePO rolePO =
                CopyBeanUtil.copy(role, RolePO.class);

        roleMapper.insert(rolePO);

        role.setId(rolePO.getId());
    }

    /**
     * 根据角色主键删除角色。
     *
     * @param roleId 角色主键
     */
    @Override
    public void deleteByRoleId(Long roleId) {
        if (roleId != null) {
            roleMapper.deleteById(roleId);
        }
    }

    /**
     * 根据主键更新角色。
     *
     * @param role 角色信息
     */
    @Override
    public void update(Role role) {
        roleMapper.updateById(
                CopyBeanUtil.copy(role, RolePO.class)
        );
    }

    /**
     * 根据角色名称查询角色简要信息，并按创建时间倒序排列。
     *
     * @param roleName 角色名称
     * @return 角色简要信息列表
     */
    @Override
    public List<RoleBrief>
    selectBriefListByRoleNameAndDescOrderByCreateTime(
            String roleName) {

        List<RolePO> rolePOList = roleMapper.selectList(
                briefQuery()
                        .like(
                                StringUtils.hasText(roleName),
                                RolePO::getRoleName,
                                roleName
                        )
                        .orderByDesc(RolePO::getCreateTime)
        );

        return CopyBeanUtil.copyList(
                rolePOList,
                RoleBrief.class
        );
    }

    /**
     * 查询全部角色简要信息。
     *
     * @return 角色简要信息列表
     */
    @Override
    public List<RoleBrief> selectAllBrief() {
        List<RolePO> rolePOList = roleMapper.selectList(
                briefQuery()
                        .orderByAsc(RolePO::getRoleName)
                        .orderByAsc(RolePO::getId)
        );

        return CopyBeanUtil.copyList(
                rolePOList,
                RoleBrief.class
        );
    }

    /**
     * 根据角色主键集合查询角色简要信息。
     *
     * @param roleIdList 角色主键集合
     * @return 角色简要信息列表
     */
    @Override
    public List<RoleBrief> selectBriefListByRoleIdList(
            List<Long> roleIdList) {

        if (roleIdList == null || roleIdList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<RolePO> rolePOList = roleMapper.selectList(
                briefQuery()
                        .in(RolePO::getId, roleIdList)
        );

        return CopyBeanUtil.copyList(
                rolePOList,
                RoleBrief.class
        );
    }

    /**
     * 查询同名角色数量，并排除指定角色。
     *
     * @param roleName 角色名称
     * @param roleId   需要排除的角色主键
     * @return 同名角色数量
     */
    @Override
    public int selectCountByRoleNameAndNotRoleId(
            String roleName,
            Long roleId) {

        Long count = roleMapper.selectCount(
                Wrappers.<RolePO>lambdaQuery()
                        .eq(RolePO::getRoleName, roleName)
                        .ne(
                                roleId != null,
                                RolePO::getId,
                                roleId
                        )
        );

        return Math.toIntExact(count);
    }

    /**
     * 创建角色简要信息查询条件。
     */
    private LambdaQueryWrapper<RolePO> briefQuery() {
        return Wrappers.<RolePO>lambdaQuery()
                .select(
                        RolePO::getId,
                        RolePO::getRoleName
                );
    }
}