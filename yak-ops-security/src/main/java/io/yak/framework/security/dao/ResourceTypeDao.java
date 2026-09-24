package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.entity.ResourceType;

import java.util.List;

/**
 * 资源类型数据访问接口。
 */
public interface ResourceTypeDao {
    List<ResourceType> selectAll();

    IPage<ResourceType> selectPage(ResourceTypeQueryDTO var1);

    ResourceType selectByResourceTypeId(Long resourceTypeId);

    void insertBatch(List<ResourceType> var1);
}
