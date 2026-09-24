package io.yak.ops.security.common.dto.dept;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
/**
 * 部门数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class DeptDTO {
  /** 部门名称。 */
  private String deptName;
  /** 描述。 */
  private String description;
  /** 子部门列表。 */
  private List<DeptDTO> childDeptDTOList;

  public List<DeptDTO> getChildDeptDTOList() {
    if (this.childDeptDTOList == null) {
      this.childDeptDTOList = new ArrayList<DeptDTO>();
    }
    return this.childDeptDTOList;
  }

}
