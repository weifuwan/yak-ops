package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.service.BaseServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessProcessServiceImpl extends BaseServiceImpl implements BusinessProcessService {

    @Resource
    private BusinessProcessDao businessProcessDao;

    @Resource
    private DataDomainDao dataDomainDao;

    @Resource
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(BusinessProcessDTO dto) {
        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.initInsert();
        businessProcessDao.insert(po);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO updateById(BusinessProcessDTO dto) {
        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.initUpdate();
        if (dto.getId() == null) {
            throw new IllegalArgumentException("主键ID不能为空");
        }
        businessProcessDao.updateById(po);
        return ConvertUtil.sourceToTarget(po, BusinessProcessVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        businessProcessDao.deleteById(id);
        return id;
    }

    @Override
    public BusinessProcessVO selectById(String id) {
        BusinessProcessPO po = businessProcessDao.queryById(id);
        if (po.getDataDomainId() == null) {
            throw new IllegalArgumentException("数据域ID不能为空");
        }
        DataDomainPO dataDomainPO = dataDomainDao.queryById(po.getDataDomainId());
        if (dataDomainPO == null) {
            throw new IllegalArgumentException("未找到数据域");
        }
        BusinessProcessVO vo = ConvertUtil.sourceToTarget(po, BusinessProcessVO.class);
        vo.setDataDomainCnName(dataDomainPO.getCnName());
        return vo;
    }

    @Override
    public IPage<BusinessProcessVO> paging(BusinessProcessDTO dto) {
        IPage<BusinessProcessPO> result = businessProcessDao.paging(
                new Page<BusinessProcessPO>(dto.getPageNo(), dto.getPageSize()),
                dto.getEnShortName(),
                dto.getCnName(),
                dto.getEnName(),
                dto.getDataDomainId(),
                dto.getDirector(),
                dto.getCreateBy());
        List<BusinessProcessVO> records = result.getRecords().stream().map(item -> {
            BusinessProcessVO vo = ConvertUtil.sourceToTarget(item, BusinessProcessVO.class);
            vo.setDataDomainCnName(item.getDataDomainCnName());
            return vo;
        }).collect(Collectors.toList());
        Page<BusinessProcessVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
    }

    @Override
    public List<BusinessProcessVO> all(BusinessProcessDTO dto) {
        LambdaQueryWrapper<BusinessProcessPO> queryWrapper = new LambdaQueryWrapper<>();

        return businessProcessDao.queryList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, BusinessProcessVO.class)).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDomainId(String dataDomainId) {
        LambdaQueryWrapper<BusinessProcessPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessProcessPO::getDataDomainId, dataDomainId);
        businessProcessDao.deleteByWrapper(wrapper);
    }

    @Override
    public List<CascaderTreeVO> treeList(String busTypeId) {
        // 1. 获取业务处理列表
        List<BusinessProcessPO> businessProcessList = getFilteredBusinessProcessList(busTypeId);

        // 2. 如果根据busTypeId没有查询到数据，则返回所有数据
        if (CollectionUtils.isEmpty(businessProcessList)) {
            businessProcessList = businessProcessDao.queryAll();
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
        List<BusinessProcessPO> allBusinessProcessList = businessProcessDao.queryAll();

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

        return businessTypeRelatedDataDomainDao.queryList(wrapper).stream()
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

        return dataDomainDao.queryList(wrapper);
    }

    /**
     * 创建CascaderTreeVO对象的辅助方法
     */
    private CascaderTreeVO createCascaderTreeVO(String id, String parentId, String name, String value, List<CascaderTreeVO> children) {
        return new CascaderTreeVO(id, parentId, name, value, children);
    }
}
