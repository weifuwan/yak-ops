package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.infrastructure.persistence.entity.businessprocess.BusinessProcessPO;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.related.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.modeling.infrastructure.persistence.entity.datadomain.DataDomainPO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import io.yak.ops.modeling.application.service.BusinessTypeRelatedDataDomainService;
import io.yak.ops.modeling.application.service.DataDomainService;
import io.yak.ops.modeling.infrastructure.persistence.mapper.BusinessProcessMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessProcessServiceImpl extends ServiceImpl<BusinessProcessMapper, BusinessProcessPO> implements BusinessProcessService {

    @Resource
    private DataDomainService dataDomainService;

    @Resource
    private BusinessTypeRelatedDataDomainService businessTypeRelatedDataDomainService;


    @Override
    public String create(BusinessProcessDTO dto) {
        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.initInsert();
        save(po);
        return po.getId();
    }

    @Override
    public BusinessProcessVO updateById(BusinessProcessDTO dto) {
        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.initUpdate();
        if (dto.getId() == null) {
            throw new RuntimeException("主键ID不能为空");
        }
        updateById(po);
        return ConvertUtil.sourceToTarget(po, BusinessProcessVO.class);
    }

    @Override
    public String delete(String id) {
        removeById(id);
        return id;
    }

    @Override
    public BusinessProcessVO selectById(String id) {
        BusinessProcessPO po = getById(id);
        if (po.getDataDomainId() == null) {
            throw new RuntimeException("数据域ID不能为空");
        }
        DataDomainVO dataDomainVO = dataDomainService.selectById(po.getDataDomainId());
        if (dataDomainVO == null) {
            throw new RuntimeException("未找到数据域");
        }
        BusinessProcessVO vo = ConvertUtil.sourceToTarget(po, BusinessProcessVO.class);
        vo.setDataDomainCnName(dataDomainVO.getCnName());
        return vo;
    }

    @Override
    public PaginationResult<BusinessProcessVO> paging(BusinessProcessDTO dto) {
        Page<BusinessProcessVO> page = new Page<>(dto.getPageNo(), dto.getPageSize());
        IPage<BusinessProcessVO> result = baseMapper.paging(page, dto);
        return PaginationResult.buildSuc(result.getRecords(), page);
    }

    @Override
    public List<BusinessProcessVO> all(BusinessProcessDTO dto) {
        LambdaQueryWrapper<BusinessProcessPO> queryWrapper = new LambdaQueryWrapper<>();

        return baseMapper.selectList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, BusinessProcessVO.class)).collect(Collectors.toList());
    }

    @Override
    public void deleteByDomainId(String dataDomainId) {
        LambdaQueryWrapper<BusinessProcessPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessProcessPO::getDataDomainId, dataDomainId);
        remove(wrapper);
    }

    @Override
    public List<CascaderTreeVO> treeList(String busTypeId) {
        // 1. 获取业务处理列表
        List<BusinessProcessPO> businessProcessList = getFilteredBusinessProcessList(busTypeId);

        // 2. 如果根据busTypeId没有查询到数据，则返回所有数据
        if (CollectionUtils.isEmpty(businessProcessList)) {
            businessProcessList = list();
        }

        // 3. 如果最终还是没有数据，返回空列表
        if (CollectionUtils.isEmpty(businessProcessList)) {
            return new ArrayList<>();
        }

        // 4. 构建级联树形结构结果
        return buildCascaderTreeResult(businessProcessList);
    }

    /**
     * 根据业务类型ID获取过滤后的业务处理列表
     */
    private List<BusinessProcessPO> getFilteredBusinessProcessList(String busTypeId) {
        List<BusinessProcessPO> allBusinessProcessList = list();

        // 如果busTypeId为空，直接返回所有数据
        if (StringUtils.isEmpty(busTypeId)) {
            return allBusinessProcessList;
        }

        // 根据busTypeId过滤数据
        Set<String> dataSet = getDataDomainIdsByBusTypeId(busTypeId);

        // 如果根据busTypeId没有找到关联的数据域，返回空列表（外层会处理返回所有数据）
        if (CollectionUtils.isEmpty(dataSet)) {
            return new ArrayList<>();
        }

        return allBusinessProcessList.stream()
                .filter(item -> dataSet.contains(item.getDataDomainId()))
                .collect(Collectors.toList());
    }

    /**
     * 根据业务类型ID获取关联的数据域ID集合
     */
    private Set<String> getDataDomainIdsByBusTypeId(String busTypeId) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, busTypeId);

        return businessTypeRelatedDataDomainService.list(wrapper).stream()
                .map(BusinessTypeRelatedDataDomainPO::getDataId)
                .collect(Collectors.toSet());
    }

    /**
     * 构建级联树形结构结果
     */
    private List<CascaderTreeVO> buildCascaderTreeResult(List<BusinessProcessPO> businessProcessList) {
        List<CascaderTreeVO> result = new ArrayList<>();

        // 按数据域ID分组
        Map<String, List<BusinessProcessPO>> businessProcessMap = businessProcessList.stream()
                .collect(Collectors.groupingBy(BusinessProcessPO::getDataDomainId));

        // 获取所有相关的数据域
        List<DataDomainPO> dataDomainList = getDataDomainList(businessProcessMap.keySet());

        // 构建树形结构：先添加数据域节点，再添加业务处理节点
        dataDomainList.forEach(dataDomain -> {
            // 添加数据域节点
            result.add(createCascaderTreeVO(dataDomain.getId(), null, dataDomain.getCnName(), dataDomain.getId(), null));

            // 添加该数据域下的业务处理节点
            businessProcessMap.getOrDefault(dataDomain.getId(), new ArrayList<>())
                    .forEach(businessProcess ->
                            result.add(createCascaderTreeVO(
                                    businessProcess.getId(),
                                    businessProcess.getDataDomainId(),
                                    businessProcess.getCnName(),
                                    businessProcess.getId(),
                                    null
                            ))
                    );
        });

        return new TreeUtil<CascaderTreeVO>().getTreeByList(result);
    }

    /**
     * 根据数据域ID集合获取数据域列表
     */
    private List<DataDomainPO> getDataDomainList(Set<String> dataDomainIds) {
        if (CollectionUtils.isEmpty(dataDomainIds)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DataDomainPO::getId, dataDomainIds);

        return dataDomainService.list(wrapper);
    }

    /**
     * 创建CascaderTreeVO对象的辅助方法
     */
    private CascaderTreeVO createCascaderTreeVO(String id, String parentId, String name, String value, List<CascaderTreeVO> children) {
        return new CascaderTreeVO(id, parentId, name, value, children);
    }
}
