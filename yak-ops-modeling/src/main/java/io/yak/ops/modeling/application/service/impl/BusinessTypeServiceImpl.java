package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.BusinessTypePO;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.related.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.modeling.infrastructure.persistence.entity.datadomain.DataDomainPO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessTypeRelatedDataDomainService;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import io.yak.ops.modeling.application.service.DataDomainService;
import io.yak.ops.modeling.infrastructure.persistence.mapper.BusinessTypeMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BusinessTypeServiceImpl extends ServiceImpl<BusinessTypeMapper, BusinessTypePO> implements BusinessTypeService {

    @Resource
    private DataDomainService dataDomainService;

    @Resource
    private BusinessTypeRelatedDataDomainService businessTypeRelatedDataDomainService;

    @Override
    public String create(BusinessTypeDTO businessType) {
        BusinessTypePO businessTypePO = ConvertUtil.sourceToTarget(businessType, BusinessTypePO.class);
        businessTypePO.initInsert();
        save(businessTypePO);
        return businessTypePO.getId();
    }

    @Override
    public BusinessTypeVO updateById(BusinessTypeDTO businessType) {
        BusinessTypePO businessTypePO = ConvertUtil.sourceToTarget(businessType, BusinessTypePO.class);
        businessTypePO.initUpdate();
        int rows = getBaseMapper().updateById(businessTypePO);
        return ConvertUtil.sourceToTarget(businessTypePO, BusinessTypeVO.class);
    }

    @Override
    public String delete(String id) {
        removeById(id);
        return id;
    }

    @Override
    public BusinessTypeVO selectById(String id) {
        BusinessTypePO businessTypePO = getById(id);
        return ConvertUtil.sourceToTarget(businessTypePO, BusinessTypeVO.class);
    }

    @Override
    public PaginationResult<BusinessTypeVO> paging(BusinessTypeDTO dto) {
        IPage<BusinessTypePO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<BusinessTypePO> wrapper = new LambdaQueryWrapper<>();
        if (dto.getId() != null) {
            wrapper.eq(BusinessTypePO::getParentId, dto.getId());
        }
        wrapper.orderByDesc(BusinessTypePO::getUpdateTime);
        IPage<BusinessTypePO> result = page(iPage, wrapper);
        List<BusinessTypeVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), BusinessTypeVO.class);
        return PaginationResult.buildSuc(
                records,
                iPage
        );
    }

    @Override
    public List<TreeVO> tree(String keyword, String id) {

        List<TreeVO> dataList = new ArrayList<>();

        LambdaQueryWrapper<BusinessTypePO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BusinessTypePO::getUpdateTime); // 按排序字段排序

        // 如果没有关键字，返回完整树
        if (StringUtils.isBlank(keyword)) {
            return buildFullTree(wrapper);
        }

        // 有关键字时，先查找匹配的节点
        wrapper.and(w -> w
                .like(BusinessTypePO::getBusinessName, keyword)
                .or()
                .like(BusinessTypePO::getEnShortName, keyword)
        );

        List<BusinessTypePO> matchedList = baseMapper.selectList(wrapper);

        if (matchedList.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有需要显示的节点ID（匹配的节点+所有父节点）
        Set<String> includeIds = new HashSet<>();
        for (BusinessTypePO item : matchedList) {
            includeIds.add(item.getId());
            // 递归添加所有父节点
            addAllParentIds(item.getParentId() == null ? null : item.getParentId(), includeIds);
        }

        // 查询所有需要的节点
        LambdaQueryWrapper<BusinessTypePO> fullWrapper = new LambdaQueryWrapper<>();
        fullWrapper.in(BusinessTypePO::getId, includeIds);
        fullWrapper.orderByAsc(BusinessTypePO::getUpdateTime);


        baseMapper.selectList(fullWrapper).forEach(item ->
                dataList.add(new TreeVO(
                        item.getId() + "",
                        item.getId() + "",
                        item.getBusinessName(),
                        item.getParentId(),
                        null
                ))
        );

        return (new TreeUtil<TreeVO>()).getTreeByList(dataList);
    }

    @Override
    public List<BusinessTypeVO> all() {
        LambdaQueryWrapper<BusinessTypePO> queryWrapper = new LambdaQueryWrapper<>();

        return baseMapper.selectList(queryWrapper).stream().map(item ->
                ConvertUtil.sourceToTarget(item, BusinessTypeVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DataDomainVO> selectTypeData(String id) {
        List<String> dataDomainIds = getRelatedDomainIdsByBusId(id);
        List<DataDomainPO> res = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataDomainIds)) {
            res = dataDomainService.getBaseMapper().selectBatchIds(dataDomainIds);
        }

        return ConvertUtil.sourceListToTarget(res, DataDomainVO.class);
    }

    @Override
    public Boolean updateTypeData(String id, List<String> dataDomainIds) {
        List<BusinessTypeRelatedDataDomainPO> insertList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataDomainIds)) {
            dataDomainIds.forEach(item -> insertList.add(BusinessTypeRelatedDataDomainPO.builder()
                    .busTypeId(id)
                    .dataId(item)
                    .build()));
        }
        return businessTypeRelatedDataDomainService.saveBatch(insertList);
    }

    @Override
    public Boolean deleteTypeData(String id, String dataDomainId) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        if (id != null && dataDomainId != null) {
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id)
                    .eq(BusinessTypeRelatedDataDomainPO::getDataId, dataDomainId);
        }
        return businessTypeRelatedDataDomainService.remove(wrapper);
    }

    @Override
    public List<OptionVO> optionRelatedDomain(String id, boolean checked) {
        List<String> dataDomainIds = getRelatedDomainIdsByBusId(id);
        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();
        if (CollectionUtils.isNotEmpty(dataDomainIds)) {
            if (checked) {
                wrapper.in(DataDomainPO::getId, dataDomainIds);
            } else {
                wrapper.notIn(DataDomainPO::getId, dataDomainIds);
            }
        }
        return dataDomainService.getBaseMapper().selectList(wrapper).stream().map(item -> {
            OptionVO optionVO = new OptionVO();
            optionVO.setLabel(item.getCnName() + "(" + item.getEnShortName() + ")");
            optionVO.setValue(item.getId());
            return optionVO;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Boolean onLoadAllDomains(String id) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();

        if (id != null) {
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id);
        }
        businessTypeRelatedDataDomainService.getBaseMapper().delete(wrapper);
        List<BusinessTypeRelatedDataDomainPO> insertList = new ArrayList<>();
        dataDomainService.all().forEach(item -> {
            BusinessTypeRelatedDataDomainPO businessTypeRelatedDataDomainPO = new BusinessTypeRelatedDataDomainPO();
            businessTypeRelatedDataDomainPO.setBusTypeId(id);
            businessTypeRelatedDataDomainPO.setDataId(item.getId());
            insertList.add(businessTypeRelatedDataDomainPO);
        });
        return businessTypeRelatedDataDomainService.saveBatch(insertList);
    }


    // ==================================================== private ====================================================

    private List<String> getRelatedDomainIdsByBusId(String id) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            // 获取业务分类关联的数据域ID
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id);
        }

        return businessTypeRelatedDataDomainService.getBaseMapper().selectList(wrapper).stream().map(BusinessTypeRelatedDataDomainPO::getDataId).collect(Collectors.toList());
    }


    // 递归添加所有父节点ID
    private void addAllParentIds(String parentId, Set<String> includeIds) {
        if (parentId == null ) {
            return;
        }

        BusinessTypePO parent = baseMapper.selectById(parentId);
        if (parent != null) {
            includeIds.add(parent.getId());
            addAllParentIds(parent.getParentId() == null ? null : parent.getParentId(), includeIds);
        }
    }

    // 构建完整树的方法
    private List<TreeVO> buildFullTree(LambdaQueryWrapper<BusinessTypePO> wrapper) {
        List<TreeVO> dataList = new ArrayList<>();
        baseMapper.selectList(wrapper).forEach(item ->
                dataList.add(new TreeVO(
                        item.getId() + "",
                        item.getId() + "",
                        item.getBusinessName(),
                        item.getParentId(),
                        null
                ))
        );
        return (new TreeUtil<TreeVO>()).getTreeByList(dataList);
    }

}
