package io.yak.ops.security.extend;

import io.yak.framework.common.PagingData;
import io.yak.ops.security.common.dto.resource.ResourceDTO;

import java.util.List;

/**
 * 业务资源扩展点。
 *
 * <p>安全模块通过该接口读取宿主系统中的业务资源，
 * 用于权限计算、资源授权和数据隔离。</p>
 *
 * @author weifuwan
 */
public interface ResourceExtend {

  /**
   * 分页查询资源。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @param resourceName 资源名称
   * @param page 页码，从 1 开始
   * @param size 每页数量
   * @return 资源分页数据，不应返回 {@code null}
   */
  PagingData<ResourceDTO> getResourcePage(
          Long projectId,
          Long resourceTypeId,
          String resourceName,
          int page,
          int size);

  /**
   * 查询资源列表。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @return 资源列表，不应返回 {@code null}
   */
  List<ResourceDTO> getResourceList(
          Long projectId,
          Long resourceTypeId);

  /**
   * 查询资源数量。
   *
   * @param projectId 项目 ID
   * @param resourceTypeId 资源类型 ID
   * @return 资源数量
   */
  int getResourceCnt(
          Long projectId,
          Long resourceTypeId);
}