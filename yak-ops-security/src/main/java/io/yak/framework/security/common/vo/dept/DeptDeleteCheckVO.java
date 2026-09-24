package io.yak.framework.security.common.vo.dept;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门删除前检查结果。
 *
 * @author weifuwan
 */
@Data
public class DeptDeleteCheckVO {

    /**
     * 部门标识。
     */
    private Long deptId;

    /**
     * 是否允许删除。
     */
    private boolean deletable;

    /**
     * 直属子部门名称列表。
     */
    private List<String> childDeptNameList =
            new ArrayList<>();

    /**
     * 直属用户名称列表。
     */
    private List<String> userNameList =
            new ArrayList<>();
}