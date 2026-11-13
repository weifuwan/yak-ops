package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.service.BaseServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import io.yak.ops.modeling.application.service.DataMartService;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataMartServiceImpl extends BaseServiceImpl implements DataMartService {

    @Resource
    private BusinessTypeService businessTypeService;

    @Resource
    private DataMartDao dataMartDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DataMartDTO dto) {
        DataMartPO po = ConvertUtil.sourceToTarget(dto, DataMartPO.class);
        po.initInsert();
        dataMartDao.insert(po);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataMartVO updateById(DataMartDTO dto) {
        DataMartPO po = ConvertUtil.sourceToTarget(dto, DataMartPO.class);
        po.initUpdate();
        dataMartDao.updateById(po);
        return ConvertUtil.sourceToTarget(po, DataMartVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        dataMartDao.deleteById(id);
        return id;
    }

    @Override
    public DataMartVO selectById(String id) {
        DataMartPO po = dataMartDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("数据集市不存在");
        }
        DataMartVO vo = ConvertUtil.sourceToTarget(po, DataMartVO.class);
        vo.setMartTypeName(vo.getMartType().getName());

        if (vo.getBusinessTypeId() != null) {
            BusinessTypeVO businessTypeVO = businessTypeService.selectById(vo.getBusinessTypeId());
            vo.setBusinessTypeName(businessTypeVO.getBusinessName());
        }
        return vo;
    }

    @Override
    public IPage<DataMartVO> paging(DataMartDTO dto) {
        IPage<DataMartPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        if (dto.getId() != null) {
            wrapper.eq(DataMartPO::getParentId, dto.getId());
        }
        wrapper.orderByDesc(DataMartPO::getUpdateTime);
        IPage<DataMartPO> result = dataMartDao.queryPage(iPage, wrapper);
        List<DataMartVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DataMartVO.class);
        Page<DataMartVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
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

        List<DataMartPO> matchedList = dataMartDao.queryList(wrapper);

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

        dataMartDao.queryList(fullWrapper).forEach(item ->
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

        return dataMartDao.queryList(queryWrapper).stream().map(item -> ConvertUtil.sourceToTarget(item, DataMartVO.class)).collect(Collectors.toList());
    }

    // ==================================================== private ====================================================

    // 递归添加所有父节点ID
    private void addAllParentIds(String parentId, Set<String> includeIds) {
        if (parentId == null ) {
            return;
        }

        DataMartPO parent = dataMartDao.queryById(parentId);
        if (parent != null) {
            includeIds.add(parent.getId());
            addAllParentIds(parent.getParentId() == null ? null : parent.getParentId(), includeIds);
        }
    }

    // 构建完整树的方法
    private List<TreeVO> buildFullTree(LambdaQueryWrapper<DataMartPO> wrapper) {
        List<TreeVO> dataList = new ArrayList<>();
        dataMartDao.queryList(wrapper).forEach(item ->
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
