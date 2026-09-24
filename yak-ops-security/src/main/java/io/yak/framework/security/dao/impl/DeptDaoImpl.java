package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import io.yak.framework.security.common.po.DeptPO;
import io.yak.framework.security.dao.DeptDao;
import io.yak.framework.security.dao.mapper.DeptMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门数据访问实现。
 */
@Repository
@RequiredArgsConstructor
public class DeptDaoImpl implements DeptDao {

    private final DeptMapper deptMapper;

    @Override
    public List<Dept> selectAllAndAscOrderByLevel() {
        List<DeptPO> departments =
                deptMapper.selectList(
                        Wrappers.<DeptPO>lambdaQuery()
                                .orderByAsc(DeptPO::getLevel)
                                .orderByAsc(DeptPO::getId));

        return CopyBeanUtil.copyList(
                departments,
                Dept.class);
    }

    @Override
    public Dept selectByDeptId(Long deptId) {
        if (deptId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                deptMapper.selectById(deptId),
                Dept.class);
    }

    @Override
    public List<Dept> selectListByParentId(
            Long parentId) {

        List<DeptPO> departmentList =
                deptMapper.selectList(
                        Wrappers.<DeptPO>lambdaQuery()
                                .eq(
                                        DeptPO::getParentId,
                                        parentId)
                                .orderByAsc(DeptPO::getLevel)
                                .orderByAsc(DeptPO::getId));

        return CopyBeanUtil.copyList(
                departmentList,
                Dept.class);
    }

    @Override
    public int countByParentId(Long parentId) {
        Long count =
                deptMapper.selectCount(
                        Wrappers.<DeptPO>lambdaQuery()
                                .eq(
                                        DeptPO::getParentId,
                                        parentId));

        return Math.toIntExact(count);
    }

    @Override
    public int countByNameAndParentId(
            String deptName,
            Long parentId,
            Long excludedDeptId) {

        Long count =
                deptMapper.selectCount(
                        Wrappers.<DeptPO>lambdaQuery()
                                .eq(
                                        DeptPO::getDeptName,
                                        deptName)
                                .eq(
                                        DeptPO::getParentId,
                                        parentId)
                                .ne(
                                        excludedDeptId != null,
                                        DeptPO::getId,
                                        excludedDeptId));

        return Math.toIntExact(count);
    }

    @Override
    public void insert(Dept dept) {
        if (dept == null) {
            return;
        }

        DeptPO deptPO =
                CopyBeanUtil.copy(
                        dept,
                        DeptPO.class);

        deptMapper.insert(deptPO);

        dept.setId(deptPO.getId());
    }

    @Override
    public void update(Dept dept) {
        if (dept == null || dept.getId() == null) {
            return;
        }

        deptMapper.updateById(
                CopyBeanUtil.copy(
                        dept,
                        DeptPO.class));
    }

    @Override
    public void updateLeaf(
            Long deptId,
            boolean leaf) {

        if (deptId == null) {
            return;
        }

        deptMapper.update(
                null,
                Wrappers.<DeptPO>lambdaUpdate()
                        .eq(
                                DeptPO::getId,
                                deptId)
                        .set(
                                DeptPO::getLeaf,
                                leaf));
    }

    @Override
    public void updateLevel(
            Long deptId,
            int level) {

        if (deptId == null) {
            return;
        }

        deptMapper.update(
                null,
                Wrappers.<DeptPO>lambdaUpdate()
                        .eq(
                                DeptPO::getId,
                                deptId)
                        .set(
                                DeptPO::getLevel,
                                level));
    }

    @Override
    public boolean deleteByDeptId(Long deptId) {
        return deptId != null
                && deptMapper.deleteById(deptId) > 0;
    }

    @Override
    public List<Long> selectIdListByLikeDeptName(
            String deptName) {

        LambdaQueryWrapper<DeptPO> wrapper =
                Wrappers.<DeptPO>lambdaQuery()
                        .select(DeptPO::getId)
                        .like(
                                StringUtils.hasText(deptName),
                                DeptPO::getDeptName,
                                deptName);

        return selectIdList(wrapper);
    }

    @Override
    public DeptBrief selectBriefByDeptId(
            Long deptId) {

        DeptPO department =
                deptMapper.selectOne(
                        briefQuery()
                                .eq(
                                        DeptPO::getId,
                                        deptId));

        return CopyBeanUtil.copy(
                department,
                DeptBrief.class);
    }

    @Override
    public List<Long> selectAllDeptIdList() {
        return selectIdList(
                Wrappers.<DeptPO>lambdaQuery()
                        .select(DeptPO::getId));
    }

    @Override
    public List<Long> selectIdListByParentId(
            Long parentId) {

        return selectIdList(
                Wrappers.<DeptPO>lambdaQuery()
                        .select(DeptPO::getId)
                        .eq(
                                DeptPO::getParentId,
                                parentId));
    }

    @Override
    public void insertBatch(List<Dept> deptList) {
        if (deptList == null || deptList.isEmpty()) {
            return;
        }

        CopyBeanUtil.copyList(
                deptList,
                DeptPO.class)
                .forEach(deptMapper::insert);
    }

    @Override
    public List<DeptBrief> selectAllDeptBriefList() {
        List<DeptPO> departments =
                deptMapper.selectList(
                        briefQuery()
                                .orderByAsc(DeptPO::getLevel)
                                .orderByAsc(DeptPO::getId));

        return CopyBeanUtil.copyList(
                departments,
                DeptBrief.class);
    }

    /**
     * 创建部门简要信息查询条件。
     */
    private LambdaQueryWrapper<DeptPO> briefQuery() {
        return Wrappers.<DeptPO>lambdaQuery()
                .select(
                        DeptPO::getId,
                        DeptPO::getDeptName,
                        DeptPO::getDescription,
                        DeptPO::getParentId,
                        DeptPO::getLeaf,
                        DeptPO::getLevel);
    }

    /**
     * 查询并转换部门主键。
     */
    private List<Long> selectIdList(
            LambdaQueryWrapper<DeptPO> wrapper) {

        return deptMapper.selectObjs(wrapper)
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .collect(Collectors.toList());
    }
}