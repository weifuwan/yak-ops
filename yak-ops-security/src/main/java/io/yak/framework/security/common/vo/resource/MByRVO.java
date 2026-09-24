package io.yak.framework.security.common.vo.resource;

import lombok.Data;
/**
 * 按资源查询的授权汇总视图对象。
 *
 * @author weifuwan
 */
@Data
public class MByRVO {
  /** 管理员用户数量。 */
  private Integer adminUserCnt;
  /** 只读用户数量。 */
  private Integer viewUserCnt;
  /** 项目标识。 */
  private Long projectId;
  /** 项目编码。 */
  private String projectCode;
  /** 项目名称。 */
  private String projectName;
  /** 资源类型标识。 */
  private Long resourceTypeId;
  /** 资源类型名称。 */
  private String resourceTypeName;
  /** 资源标识。 */
  private Long resourceId;
  /** 资源名称。 */
  private String resourceName;

}
