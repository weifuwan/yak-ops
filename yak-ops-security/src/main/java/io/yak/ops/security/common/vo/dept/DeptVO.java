package io.yak.ops.security.common.vo.dept;

import lombok.Data;

/**
 * 部门详情视图对象。
 *
 * @author weifuwan
 */
@Data
public class DeptVO {

    /**
     * 部门标识。
     */
    private Long id;

    /**
     * 部门名称。
     */
    private String deptName;

    /**
     * 部门描述。
     */
    private String description;

    /**
     * 上级部门标识。
     */
    private Long parentId;

    /**
     * 是否为叶子部门。
     */
    private Boolean leaf;

    /**
     * 部门层级。
     */
    private Integer level;

    /**
     * 直属子部门数量。
     */
    private Integer childDeptCount;

    /**
     * 直属用户数量。
     */
    private Integer userCount;
}