package io.yak.framework.security.common.dto.role;

import lombok.Data;

import java.util.List;
/**
 * 角色分配数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class RoleAssignDTO {
  /** 标识。 */
  private Long id;
  /** 资源标识列表。 */
  private List<Long> idList;
  /** 操作标记。 */
  private Boolean flag;

}
