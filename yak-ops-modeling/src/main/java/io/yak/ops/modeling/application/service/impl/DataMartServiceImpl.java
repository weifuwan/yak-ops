package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.infrastructure.persistence.entity.datamart.DataMartPO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import io.yak.ops.modeling.application.service.DataMartService;
import io.yak.ops.modeling.infrastructure.persistence.mapper.DataMartMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataMartServiceImpl extends ServiceImpl<DataMartMapper, DataMartPO> implements DataMartService {

    @Resource
    private BusinessTypeService businessTypeService;

    @Override
    public String create(DataMartDTO DataMart) {
        DataMartPO DataMartPO = ConvertUtil.sourceToTarget(DataMart, DataMartPO.class);
        DataMartPO.initInsert();
        save(DataMartPO);
        return DataMartPO.getId();
    }

    @Override
    public DataMartVO updateById(DataMartDTO DataMart) {
        DataMartPO DataMartPO = ConvertUtil.sourceToTarget(DataMart, DataMartPO.class);
        DataMartPO.initUpdate();
        updateById(DataMartPO);
        return ConvertUtil.sourceToTarget(DataMartPO, DataMartVO.class);
    }

    @Override
    public String delete(String id) {
        removeById(id);
        return id;
    }

    @Override
    public DataMartVO selectById(String id) {
        DataMartPO DataMartPO = getById(id);
        DataMartVO dataMartVO = ConvertUtil.sourceToTarget(DataMartPO, DataMartVO.class);
        dataMartVO.setMartTypeName(dataMartVO.getMartType().getName());

        // 获取业务分类中文名称
        if (dataMartVO.getBusinessTypeId() != null) {
            BusinessTypeVO businessTypeVO = businessTypeService.selectById(dataMartVO.getBusinessTypeId());
            dataMartVO.setBusinessTypeName(businessTypeVO.getBusinessName());
        }
        return dataMartVO;
    }

    @Override
    public PaginationResult<DataMartVO> paging(DataMartDTO dto) {
        IPage<DataMartPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        if (dto.getId() != null) {
            wrapper.eq(DataMartPO::getParentId, dto.getId());
        }
        wrapper.orderByDesc(DataMartPO::getUpdateTime);
        IPage<DataMartPO> result = page(iPage, wrapper);
        List<DataMartVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DataMartVO.class);
        return PaginationResult.buildSuc(
                records,
                iPage
        );
    }

    @Override
    public List<TreeVO> tree(String keyword) {

        List<TreeVO> dataList = new ArrayList<>();

        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DataMartPO::getUpdateTime); // 按排序字段排序

        // 如果没有关键字，返回完整树
        if (StringUtils.isBlank(keyword)) {
            return buildFullTree(wrapper);
        }

        // 有关键字时，先查找匹配的节点
        wrapper.and(w -> w
                .like(DataMartPO::getMartName, keyword)
                .or()
                .like(DataMartPO::getEnShortName, keyword)
        );

        List<DataMartPO> matchedList = baseMapper.selectList(wrapper);

        if (matchedList.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有需要显示的节点ID（匹配的节点+所有父节点）
        Set<String> includeIds = new HashSet<>();
        for (DataMartPO item : matchedList) {
            includeIds.add(item.getId());
            // 递归添加所有父节点
            addAllParentIds(item.getParentId() == null ? null : item.getParentId(), includeIds);
        }

        // 查询所有需要的节点
        LambdaQueryWrapper<DataMartPO> fullWrapper = new LambdaQueryWrapper<>();
        fullWrapper.in(DataMartPO::getId, includeIds);
        fullWrapper.orderByAsc(DataMartPO::getUpdateTime);

        baseMapper.selectList(fullWrapper).forEach(item ->
                dataList.add(new TreeVO(
                        item.getId() + "",
                        item.getId() + "",
                        item.getMartName(),
                        item.getParentId(),
                        null
                ))
        );

        return (new TreeUtil<TreeVO>()).getTreeByList(dataList);

    }

    @Override
    public List<DataMartVO> all() {
        LambdaQueryWrapper<DataMartPO> queryWrapper = new LambdaQueryWrapper<>();

        return baseMapper.selectList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, DataMartVO.class)).collect(Collectors.toList());
    }

    // ==================================================== private ====================================================

    // 递归添加所有父节点ID
    private void addAllParentIds(String parentId, Set<String> includeIds) {
        if (parentId == null ) {
            return;
        }

        DataMartPO parent = baseMapper.selectById(parentId);
        if (parent != null) {
            includeIds.add(parent.getId());
            addAllParentIds(parent.getParentId() == null ? null : parent.getParentId(), includeIds);
        }
    }

    // 构建完整树的方法
    private List<TreeVO> buildFullTree(LambdaQueryWrapper<DataMartPO> wrapper) {
        List<TreeVO> dataList = new ArrayList<>();
        baseMapper.selectList(wrapper).forEach(item ->
                dataList.add(new TreeVO(
                        item.getId() + "",
                        item.getId() + "",
                        item.getMartName(),
                        item.getParentId(),
                        null
                ))
        );
        return (new TreeUtil<TreeVO>()).getTreeByList(dataList);
    }

}
