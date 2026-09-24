package io.yak.framework.security.dao;

import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;

import java.util.List;

/**
 * 部门数据访问接口。
 */
public interface DeptDao {

    /**
     * 查询全部部门，并按照层级升序排列。
     */
    List<Dept> selectAllAndAscOrderByLevel();

    /**
     * 根据部门标识查询部门。
     */
    Dept selectByDeptId(Long deptId);

    /**
     * 查询直属子部门。
     */
    List<Dept> selectListByParentId(Long parentId);

    /**
     * 查询直属子部门数量。
     */
    int countByParentId(Long parentId);

    /**
     * 查询同一上级部门下同名部门数量。
     *
     * @param deptName 部门名称
     * @param parentId 上级部门标识
     * @param excludedDeptId 编辑时需要排除的部门标识
     */
    int countByNameAndParentId(
            String deptName,
            Long parentId,
            Long excludedDeptId);

    /**
     * 新增部门并回填主键。
     */
    void insert(Dept dept);

    /**
     * 更新部门。
     */
    void update(Dept dept);

    /**
     * 更新部门叶子节点状态。
     */
    void updateLeaf(Long deptId, boolean leaf);

    /**
     * 更新部门层级。
     */
    void updateLevel(Long deptId, int level);

    /**
     * 根据部门标识删除部门。
     */
    boolean deleteByDeptId(Long deptId);

    List<Long> selectIdListByLikeDeptName(String deptName);

    DeptBrief selectBriefByDeptId(Long deptId);

    List<Long> selectAllDeptIdList();

    List<Long> selectIdListByParentId(Long parentId);

    void insertBatch(List<Dept> deptList);

    List<DeptBrief> selectAllDeptBriefList();
}