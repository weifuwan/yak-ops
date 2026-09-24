package io.yak.ops.security.common.entity;

import java.util.Date;
import lombok.Data;

/**
 * 基础实体。
 *
 * @author weifuwan
 */
@Data
public class BaseEntity {
  /** 实体标识。 */
  private Long id;
  /** 创建时间。 */
  private Date createTime;
  /** 更新时间。 */
  private Date updateTime;
  /** 删除标记。 */
  private Boolean isDelete = false;

}
