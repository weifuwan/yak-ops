package io.yak.framework.security.common.dto.project;

import lombok.Data;

import java.util.List;

/**
 * 项目用户分配数据传输对象。
 *
 * @author weifuwan
 */
@Data
public class ProjectUserAssignDTO {

  /**
   * 用户标识列表。
   *
   * <p>空列表表示清空当前类型的全部项目用户。
   */
  private List<Long> userIdList;
}
