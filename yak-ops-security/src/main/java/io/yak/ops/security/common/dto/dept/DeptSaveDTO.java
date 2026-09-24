package io.yak.ops.security.common.dto.dept;

import lombok.Data;

/**
 * 部门新增及编辑数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class DeptSaveDTO {

    /**
     * 部门标识。
     *
     * <p>新增时为空，编辑时必填。
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
     *
     * <p>值为 0 时表示根部门。
     */
    private Long parentId;
}