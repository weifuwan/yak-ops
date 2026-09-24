package io.yak.ops.security.extend.impl;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.security.common.dto.resource.ResourceDTO;
import io.yak.ops.security.extend.ResourceExtend;

import java.util.Collections;
import java.util.List;

/**
 * 默认资源扩展实现。
 *
 * <p>宿主应用未提供资源扩展时，默认返回空资源数据。</p>
 *
 * @author weifuwan
 */
public class DefaultResourceExtendImpl
        implements ResourceExtend {

  /**
   * 默认页码。
   */
  private static final int DEFAULT_PAGE = 1;

  /**
   * 默认分页数量。
   */
  private static final int DEFAULT_SIZE = 10;

  /**
   * 返回空资源分页。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @param resourceName 资源名称
   * @param page 页码
   * @param size 每页数量
   * @return 空分页数据
   */
  @Override
  public PagingData<ResourceDTO> getResourcePage(
          Long projectId,
          Long resourceTypeId,
          String resourceName,
          int page,
          int size) {

    long currentPage =
            page > 0 ? page : DEFAULT_PAGE;

    long pageSize =
            size > 0 ? size : DEFAULT_SIZE;

    return PagingData.from(
            PageData.empty(currentPage, pageSize));
  }

  /**
   * 返回空资源列表。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @return 空资源列表
   */
  @Override
  public List<ResourceDTO> getResourceList(
          Long projectId,
          Long resourceTypeId) {

    return Collections.emptyList();
  }

  /**
   * 返回资源数量。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @return 固定返回 {@code 0}
   */
  @Override
  public int getResourceCnt(
          Long projectId,
          Long resourceTypeId) {

    return 0;
  }
}