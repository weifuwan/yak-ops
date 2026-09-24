package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.entity.ResourceType;
import io.yak.framework.security.common.vo.resource.ResourceTypeVO;
import io.yak.framework.security.dao.ResourceTypeDao;
import io.yak.framework.security.service.ResourceTypeService;
import io.yak.framework.security.util.CopyBeanUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 资源类型服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityResourceTypeServiceImpl")
public class ResourceTypeServiceImpl
        implements ResourceTypeService {

  private final ResourceTypeDao resourceTypeDao;

  /**
   * 创建资源类型服务。
   *
   * @param resourceTypeDao 资源类型数据访问对象
   */
  public ResourceTypeServiceImpl(
          ResourceTypeDao resourceTypeDao) {

    this.resourceTypeDao = resourceTypeDao;
  }

  /**
   * 查询全部资源类型。
   *
   * @return 资源类型列表
   */
  @Override
  public List<ResourceTypeVO> getAllResourceTypeList() {
    List<ResourceType> resourceTypeList =
            resourceTypeDao.selectAll();

    if (CollectionUtils.isEmpty(resourceTypeList)) {
      return new ArrayList<>();
    }

    List<ResourceTypeVO> result =
            CopyBeanUtil.copyList(
                    resourceTypeList,
                    ResourceTypeVO.class);

    return result == null
            ? new ArrayList<>()
            : result;
  }

  /**
   * 查询全部资源类型 ID。
   *
   * @return 资源类型 ID 列表
   */
  @Override
  public List<Long> getAllResourceTypeIdList() {
    List<ResourceType> resourceTypeList =
            resourceTypeDao.selectAll();

    if (CollectionUtils.isEmpty(resourceTypeList)) {
      return new ArrayList<>();
    }

    return resourceTypeList.stream()
            .filter(Objects::nonNull)
            .map(ResourceType::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }

  /**
   * 分页查询资源类型。
   *
   * @param queryDTO 查询条件
   * @return 资源类型分页数据
   */
  @Override
  public PagingData<ResourceTypeVO> getResourceTypePage(
          ResourceTypeQueryDTO queryDTO) {

    if (queryDTO == null) {
      throw new IllegalArgumentException(
              "资源类型查询条件不能为空");
    }

    IPage<ResourceType> resourceTypePage =
            resourceTypeDao.selectPage(queryDTO);

    List<ResourceTypeVO> resourceTypeList =
            CopyBeanUtil.copyList(
                    resourceTypePage.getRecords(),
                    ResourceTypeVO.class);

    if (resourceTypeList == null) {
      resourceTypeList = new ArrayList<>();
    }

    return PagingData.from(
            new PageData<>(
                    resourceTypeList,
                    resourceTypePage.getTotal(),
                    resourceTypePage.getPages(),
                    resourceTypePage.getCurrent(),
                    resourceTypePage.getSize()));
  }

  /**
   * 根据资源类型 ID 查询资源类型。
   *
   * @param resourceTypeId 资源类型 ID
   * @return 资源类型信息
   */
  @Override
  public ResourceTypeVO getResourceTypeByResourceTypeId(
          Long resourceTypeId) {

    if (resourceTypeId == null) {
      return null;
    }

    ResourceType resourceType =
            resourceTypeDao
                    .selectByResourceTypeId(
                            resourceTypeId);

    return CopyBeanUtil.copy(
            resourceType,
            ResourceTypeVO.class);
  }

  /**
   * 批量保存资源类型。
   *
   * @param resourceTypeNameList 资源类型名称列表
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveResourceType(
          List<String> resourceTypeNameList) {

    List<String> validTypeNames =
            normalizeTypeNames(
                    resourceTypeNameList);

    if (validTypeNames.isEmpty()) {
      return;
    }

    List<ResourceType> resourceTypeList =
            buildResourceTypeList(
                    validTypeNames);

    resourceTypeDao.insertBatch(
            resourceTypeList);
  }

  /**
   * 构建资源类型实体列表。
   *
   * @param resourceTypeNameList 资源类型名称列表
   * @return 资源类型实体列表
   */
  private List<ResourceType> buildResourceTypeList(
          List<String> resourceTypeNameList) {

    List<ResourceType> resourceTypeList =
            new ArrayList<>(
                    resourceTypeNameList.size());

    for (String resourceTypeName
            : resourceTypeNameList) {

      ResourceType resourceType =
              new ResourceType();

      resourceType.setTypeName(
              resourceTypeName);

      resourceTypeList.add(
              resourceType);
    }

    return resourceTypeList;
  }

  /**
   * 清理资源类型名称。
   *
   * <p>过滤空名称、去除首尾空格并去重。
   *
   * @param resourceTypeNameList 资源类型名称列表
   * @return 有效资源类型名称列表
   */
  private List<String> normalizeTypeNames(
          List<String> resourceTypeNameList) {

    if (CollectionUtils.isEmpty(
            resourceTypeNameList)) {

      return new ArrayList<>();
    }

    return resourceTypeNameList.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());
  }
}