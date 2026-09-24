package io.yak.ops.security.common.vo.resource;

import lombok.Data;

import io.yak.ops.security.common.vo.dept.DeptBriefVO;
import java.util.List;
/**
 * 按用户查询的授权汇总视图对象。
 *
 * @author weifuwan
 */
@Data
public class MByUVO {
  /** 用户标识。 */
  private Long userId;
  /** 用户名。 */
  private String userName;
  /** 用户真实姓名。 */
  private String realName;
  /** 部门列表。 */
  private List<DeptBriefVO> deptList;
  /** 具有管理权限的资源数量。 */
  private Integer adminResourceCnt;
  /** 具有查看权限的资源数量。 */
  private Integer viewResourceCnt;

}
