package io.yak.ops.common.bean.dto.security;

import lombok.Data;
/**
 * 分页参数数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class PageParamDTO {
  /** 当前页码。 */
  private int page = 1;
  /** 每页记录数。 */
  private int size = 10;

}
