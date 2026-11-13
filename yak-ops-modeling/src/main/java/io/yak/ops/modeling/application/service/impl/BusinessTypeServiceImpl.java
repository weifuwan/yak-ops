package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.service.BaseServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.businesstype.BusinessTypeDTO;
import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import io.yak.ops.modeling.application.service.DataDomainService;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BusinessTypeServiceImpl extends BaseServiceImpl implements BusinessTypeService {

    @Resource
    private DataDomainService dataDomainService;

    @Resource
    private BusinessTypeDao businessTypeDao;

    @Resource
    private DataDomainDao dataDomainDao;

    @Resource
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(BusinessTypeDTO businessType) {
        BusinessTypePO businessTypePO = ConvertUtil.sourceToTarget(businessType, BusinessTypePO.class);
        businessTypePO.initInsert();
        businessTypeDao.insert(businessTypePO);
        return businessTypePO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessTypeVO updateById(BusinessTypeDTO businessType) {
        BusinessTypePO businessTypePO = ConvertUtil.sourceToTarget(businessType, BusinessTypePO.class);
        businessTypePO.initUpdate();
        businessTypeDao.updateById(businessTypePO);
        return ConvertUtil.sourceToTarget(businessTypePO, BusinessTypeVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        businessTypeDao.deleteById(id);
        return id;
    }

    @Override
    public BusinessTypeVO selectById(String id) {
        BusinessTypePO businessTypePO = businessTypeDao.queryById(id);
        return ConvertUtil.sourceToTarget(businessTypePO, BusinessTypeVO.class);
    }

    @Override
    public IPage<BusinessTypeVO> paging(BusinessTypeDTO dto) {
        IPage<BusinessTypePO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<BusinessTypePO> wrapper = new LambdaQueryWrapper<>();
        if (dto.getId() != null) {
            wrapper.eq(BusinessTypePO::getParentId, dto.getId());
        }
        wrapper.orderByDesc(BusinessTypePO::getUpdateTime);
        IPage<BusinessTypePO> result = businessTypeDao.queryPage(iPage, wrapper);
        List<BusinessTypeVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), BusinessTypeVO.class);
        Page<BusinessTypeVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
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

        List<BusinessTypePO> matchedList = businessTypeDao.queryList(wrapper);

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


        businessTypeDao.queryList(fullWrapper).forEach(item ->
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

        return businessTypeDao.queryList(queryWrapper).stream().map(item ->
                ConvertUtil.sourceToTarget(item, BusinessTypeVO.class)).collect(Collectors.toList());
    }

    @Override
    public List<DataDomainVO> selectTypeData(String id) {
        List<String> dataDomainIds = getRelatedDomainIdsByBusId(id);
        List<DataDomainPO> res = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataDomainIds)) {
            res = dataDomainDao.queryByIds(dataDomainIds);
        }

        return ConvertUtil.sourceListToTarget(res, DataDomainVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateTypeData(String id, List<String> dataDomainIds) {
        List<BusinessTypeRelatedDataDomainPO> insertList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(dataDomainIds)) {
            dataDomainIds.forEach(item -> insertList.add(BusinessTypeRelatedDataDomainPO.builder()
                    .busTypeId(id)
                    .dataId(item)
                    .build()));
        }
        businessTypeRelatedDataDomainDao.insertBatch(insertList);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteTypeData(String id, String dataDomainId) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        if (id != null && dataDomainId != null) {
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id)
                    .eq(BusinessTypeRelatedDataDomainPO::getDataId, dataDomainId);
        }
        return businessTypeRelatedDataDomainDao.deleteByWrapper(wrapper) > 0;
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
        return dataDomainDao.queryList(wrapper).stream().map(item -> {
            OptionVO optionVO = new OptionVO();
            optionVO.setLabel(item.getCnName() + "(" + item.getEnShortName() + ")");
            optionVO.setValue(item.getId());
            return optionVO;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean onLoadAllDomains(String id) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();

        if (id != null) {
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id);
        }
        businessTypeRelatedDataDomainDao.deleteByWrapper(wrapper);
        List<BusinessTypeRelatedDataDomainPO> insertList = new ArrayList<>();
        dataDomainService.all().forEach(item -> {
            BusinessTypeRelatedDataDomainPO businessTypeRelatedDataDomainPO = new BusinessTypeRelatedDataDomainPO();
            businessTypeRelatedDataDomainPO.setBusTypeId(id);
            businessTypeRelatedDataDomainPO.setDataId(item.getId());
            insertList.add(businessTypeRelatedDataDomainPO);
        });
        businessTypeRelatedDataDomainDao.insertBatch(insertList);
        return true;
    }


    // ==================================================== private ====================================================

    private List<String> getRelatedDomainIdsByBusId(String id) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            // 获取业务分类关联的数据域ID
            wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, id);
        }

        return businessTypeRelatedDataDomainDao.queryList(wrapper).stream().map(BusinessTypeRelatedDataDomainPO::getDataId).collect(Collectors.toList());
    }


    // 递归添加所有父节点ID
    private void addAllParentIds(String parentId, Set<String> includeIds) {
        if (parentId == null ) {
            return;
        }

        BusinessTypePO parent = businessTypeDao.queryById(parentId);
        if (parent != null) {
            includeIds.add(parent.getId());
            addAllParentIds(parent.getParentId() == null ? null : parent.getParentId(), includeIds);
        }
    }

    // 构建完整树的方法
    private List<TreeVO> buildFullTree(LambdaQueryWrapper<BusinessTypePO> wrapper) {
        List<TreeVO> dataList = new ArrayList<>();
        businessTypeDao.queryList(wrapper).forEach(item ->
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
