package io.yak.ops.security.common.vo.resource;

import lombok.Data;
/**
 * 按用户查询的资源授权数据视图对象。
 *
 * @author weifuwan
 */
@Data
public class MByUDataVO {
  /** 主键标识。 */
  private Long id;
  /** 名称。 */
  private String name;
  /** 授权级别。 */
  private Integer hasLevel;

  public MByUDataVO() {}

  public MByUDataVO(Long id, String name) {
    this.id = id;
    this.name = name;
  }

}
