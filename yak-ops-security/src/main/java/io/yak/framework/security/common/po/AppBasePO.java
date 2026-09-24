package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 应用隔离持久化基类。
 *
 * @author weifuwan
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class AppBasePO {
  /** 应用名称，用于区分数据所属应用。 */
  @TableField(fill = FieldFill.INSERT)
  private String appName;
}
