package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;

import java.util.List;

/**
 * 资源类型服务接口。
 *
 * @author weifuwan
 */
public interface ResourceTypeService {

  /**
   * 查询全部资源类型。
   *
   * @return 资源类型列表
   */
  List<ResourceTypeVO> getAllResourceTypeList();

  /**
   * 查询全部资源类型 ID。
   *
   * @return 资源类型 ID 列表
   */
  List<Long> getAllResourceTypeIdList();

  /**
   * 分页查询资源类型。
   *
   * @param queryDTO 查询条件
   * @return 资源类型分页数据
   */
  PagingData<ResourceTypeVO> getResourceTypePage(
          ResourceTypeQueryDTO queryDTO);

  /**
   * 根据资源类型 ID 查询资源类型。
   *
   * @param resourceTypeId 资源类型 ID
   * @return 资源类型信息
   */
  ResourceTypeVO getResourceTypeByResourceTypeId(
          Long resourceTypeId);

  /**
   * 批量保存资源类型。
   *
   * @param resourceTypeNameList 资源类型名称列表
   */
  void saveResourceType(
          List<String> resourceTypeNameList);
}