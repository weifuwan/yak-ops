package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 操作日志扩展信息持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_oplog_extra")
public class OplogExtraPO extends BasePO {

  /**
   * 扩展信息内容。
   *
   * <p>该字段可能包含较长内容或敏感数据，
   * 因此不输出到日志字符串中。</p>
   */
  @ToString.Exclude
  private String info;

  /**
   * 扩展信息类型。
   */
  private Integer type;
}