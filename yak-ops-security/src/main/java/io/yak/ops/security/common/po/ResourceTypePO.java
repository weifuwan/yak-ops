package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 资源类型持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_resource_type")
public class ResourceTypePO extends BasePO {

  /**
   * 资源类型名称。
   */
  private String typeName;
}