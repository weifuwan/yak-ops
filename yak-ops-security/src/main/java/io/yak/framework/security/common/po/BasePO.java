package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 通用持久化基类。
 *
 * @author weifuwan
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class BasePO extends AppBasePO {
  /** 主键标识。 */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** 创建时间。 */
  private Date createTime;

  /** 最后更新时间。 */
  private Date updateTime;

  /** 逻辑删除标记：0 未删除，1 已删除。 */
  @TableLogic(value = "0", delval = "1")
  private int isDelete = 0;
}
