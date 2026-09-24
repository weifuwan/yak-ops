package io.yak.framework.security.common.vo.oplog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志筛选选项。
 *
 * @author weifuwan
 */
@Data
public class OplogOptionsVO {
  /** 操作类型列表。 */
  private List<String> operateTypes = new ArrayList<>();
  /** 操作页面列表。 */
  private List<String> operatePages = new ArrayList<>();
  /** 操作方法列表。 */
  private List<String> operationMethods = new ArrayList<>();
  /** 操作目标类型列表。 */
  private List<String> targetTypes = new ArrayList<>();
}
