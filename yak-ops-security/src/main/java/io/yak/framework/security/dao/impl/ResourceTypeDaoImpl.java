package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.resource.type.ResourceTypeQueryDTO;
import io.yak.framework.security.common.entity.ResourceType;
import io.yak.framework.security.common.po.ResourceTypePO;
import io.yak.framework.security.dao.ResourceTypeDao;
import io.yak.framework.security.dao.mapper.ResourceTypeMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 资源类型数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class ResourceTypeDaoImpl
        implements ResourceTypeDao {

    private final ResourceTypeMapper resourceTypeMapper;

    /**
     * 查询全部资源类型。
     *
     * @return 资源类型列表
     */
    @Override
    public List<ResourceType> selectAll() {
        List<ResourceTypePO> resourceTypePOList =
                resourceTypeMapper.selectList(
                        Wrappers.<ResourceTypePO>lambdaQuery()
                                .orderByAsc(ResourceTypePO::getTypeName)
                                .orderByAsc(ResourceTypePO::getId)
                );

        return CopyBeanUtil.copyList(
                resourceTypePOList,
                ResourceType.class
        );
    }

    /**
     * 分页查询资源类型。
     *
     * @param queryDTO 查询条件
     * @return 资源类型分页数据
     */
    @Override
    public IPage<ResourceType> selectPage(
            ResourceTypeQueryDTO queryDTO) {

        Page<ResourceTypePO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        IPage<ResourceTypePO> result =
                resourceTypeMapper.selectPage(
                        page,
                        Wrappers.<ResourceTypePO>lambdaQuery()
                                .like(
                                        StringUtils.hasText(
                                                queryDTO.getTypeName()
                                        ),
                                        ResourceTypePO::getTypeName,
                                        queryDTO.getTypeName()
                                )
                                .orderByDesc(ResourceTypePO::getId)
                );

        return CopyBeanUtil.copyPage(
                result,
                ResourceType.class
        );
    }

    /**
     * 根据资源类型主键查询资源类型。
     *
     * @param resourceTypeId 资源类型主键
     * @return 资源类型；主键为空或数据不存在时返回 {@code null}
     */
    @Override
    public ResourceType selectByResourceTypeId(
            Long resourceTypeId) {

        if (resourceTypeId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                resourceTypeMapper.selectById(resourceTypeId),
                ResourceType.class
        );
    }

    /**
     * 批量新增资源类型。
     *
     * <p>当前采用循环插入方式，适合资源类型数量较少的初始化场景。</p>
     *
     * @param resourceTypeList 资源类型列表
     */
    @Override
    public void insertBatch(
            List<ResourceType> resourceTypeList) {

        if (resourceTypeList == null
                || resourceTypeList.isEmpty()) {
            return;
        }

        CopyBeanUtil.copyList(
                resourceTypeList,
                ResourceTypePO.class
        )
                .forEach(resourceTypeMapper::insert);
    }
}