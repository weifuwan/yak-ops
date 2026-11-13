package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.datamart.DataMartDTO;
import io.yak.ops.modeling.api.vo.businesstype.BusinessTypeVO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.tree.TreeVO;
import io.yak.ops.modeling.application.service.BusinessTypeService;
import io.yak.ops.modeling.application.service.DataMartService;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataMartServiceImpl extends BaseServiceImpl implements DataMartService {

    @Resource
    private BusinessTypeService businessTypeService;

    @Resource
    private DataMartDao dataMartDao;

    @Resource
    private SubjectDomainDao subjectDomainDao;

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DataMartDTO dto) {
        normalizeAndValidate(dto, null);
        DataMartPO parent = validateParent(dto.getParentId(), null);
        if (parent != null) {
            validateAndInheritBusinessType(dto, parent);
        } else {
            validateBusinessType(dto.getBusinessTypeId());
        }
        validateUniqueShortName(dto.getEnShortName(), null);

        DataMartPO po = ConvertUtil.sourceToTarget(dto, DataMartPO.class);
        po.initInsert();
        dataMartDao.insert(po);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataMartVO updateById(DataMartDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("数据集市ID不能为空");
        }
        DataMartPO existing = dataMartDao.queryById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("数据集市不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getParentId()) && StringUtils.isNotBlank(existing.getParentId())) {
            dto.setParentId(existing.getParentId());
        }

        normalizeAndValidate(dto, existing.getId());
        DataMartPO parent = validateParent(dto.getParentId(), existing.getId());
        if (parent != null) {
            validateAndInheritBusinessType(dto, parent);
        } else {
            validateBusinessType(dto.getBusinessTypeId());
        }
        validateUniqueShortName(dto.getEnShortName(), existing.getId());

        DataMartPO po = ConvertUtil.sourceToTarget(dto, DataMartPO.class);
        po.setId(existing.getId());
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        if (!dataMartDao.updateById(po)) {
            throw new IllegalArgumentException("数据集市更新失败");
        }
        return selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("数据集市ID不能为空");
        }
        if (dataMartDao.queryById(id) == null) {
            throw new IllegalArgumentException("数据集市不存在或不属于当前项目");
        }

        long childCount = dataMartDao.queryCount(
                new LambdaQueryWrapper<DataMartPO>().eq(DataMartPO::getParentId, id));
        if (childCount > 0) {
            throw new IllegalArgumentException("该数据集市下存在下级数据集市（" + childCount + "条），不能删除");
        }

        long subjectCount = subjectDomainDao.queryCount(
                new LambdaQueryWrapper<SubjectDomainPO>().eq(SubjectDomainPO::getDataMartId, id));
        long dimensionCount = dimensionTableDao.queryCount(
                new LambdaQueryWrapper<DimensionTablePO>().eq(DimensionTablePO::getMartId, id));
        if (subjectCount + dimensionCount > 0) {
            throw new IllegalArgumentException(
                    "该数据集市已被引用，不能删除（主题域 " + subjectCount
                            + " 条、维度模型 " + dimensionCount + " 条）");
        }

        if (!dataMartDao.deleteById(id)) {
            throw new IllegalArgumentException("数据集市删除失败");
        }
        return id;
    }

    @Override
    public DataMartVO selectById(String id) {
        DataMartPO po = dataMartDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("数据集市不存在或不属于当前项目");
        }
        DataMartVO vo = ConvertUtil.sourceToTarget(po, DataMartVO.class);
        if (vo.getMartType() != null) {
            vo.setMartTypeName(vo.getMartType().getName());
        }
        if (StringUtils.isNotBlank(vo.getBusinessTypeId())) {
            BusinessTypeVO businessType = businessTypeService.selectById(vo.getBusinessTypeId());
            if (businessType == null) {
                throw new IllegalArgumentException("数据集市关联的业务分类不存在");
            }
            vo.setBusinessTypeName(businessType.getBusinessName());
        }
        return vo;
    }

    @Override
    public IPage<DataMartVO> paging(DataMartDTO dto) {
        IPage<DataMartPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(dto.getId())) {
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
        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DataMartPO::getUpdateTime);
        if (StringUtils.isBlank(keyword)) {
            return buildFullTree(wrapper);
        }

        wrapper.and(w -> w
                .like(DataMartPO::getMartName, keyword)
                .or()
                .like(DataMartPO::getEnShortName, keyword));
        List<DataMartPO> matched = dataMartDao.queryList(wrapper);
        if (matched.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> includeIds = new HashSet<>();
        Set<String> visitedParents = new HashSet<>();
        for (DataMartPO item : matched) {
            includeIds.add(item.getId());
            addAllParentIds(item.getParentId(), includeIds, visitedParents);
        }

        LambdaQueryWrapper<DataMartPO> fullWrapper = new LambdaQueryWrapper<>();
        fullWrapper.in(DataMartPO::getId, includeIds).orderByAsc(DataMartPO::getUpdateTime);
        return toTree(dataMartDao.queryList(fullWrapper));
    }

    @Override
    public List<DataMartVO> all() {
        LambdaQueryWrapper<DataMartPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DataMartPO::getMartName).orderByAsc(DataMartPO::getId);
        return dataMartDao.queryList(wrapper).stream()
                .map(item -> ConvertUtil.sourceToTarget(item, DataMartVO.class))
                .collect(Collectors.toList());
    }

    private void normalizeAndValidate(DataMartDTO dto, String excludeId) {
        if (dto == null) {
            throw new IllegalArgumentException("数据集市参数不能为空");
        }
        dto.setEnShortName(StringUtils.trim(dto.getEnShortName()));
        dto.setMartName(StringUtils.trim(dto.getMartName()));
        dto.setDirector(StringUtils.trim(dto.getDirector()));
        dto.setRemark(StringUtils.trim(dto.getRemark()));
        if (StringUtils.isBlank(dto.getEnShortName()) || dto.getEnShortName().length() > 64) {
            throw new IllegalArgumentException("数据集市英文缩写不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getMartName()) || dto.getMartName().length() > 64) {
            throw new IllegalArgumentException("数据集市名称不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getDirector()) || dto.getDirector().length() > 32) {
            throw new IllegalArgumentException("数据集市负责人不能为空且不能超过32个字符");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 2048) {
            throw new IllegalArgumentException("备注不能超过2048个字符");
        }
    }

    private void validateUniqueShortName(String shortName, String excludeId) {
        QueryWrapper<DataMartPO> wrapper = new QueryWrapper<>();
        wrapper.apply("LOWER(en_short_name) = {0}", shortName.toLowerCase(Locale.ROOT));
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (dataMartDao.queryCount(wrapper) > 0) {
            throw new IllegalArgumentException("数据集市英文缩写已存在：" + shortName);
        }
    }

    private void validateBusinessType(String businessTypeId) {
        if (StringUtils.isBlank(businessTypeId)) {
            return;
        }
        if (businessTypeService.selectById(businessTypeId) == null) {
            throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
        }
    }

    private void validateAndInheritBusinessType(DataMartDTO dto, DataMartPO parent) {
        String parentBusinessTypeId = parent.getBusinessTypeId();
        if (StringUtils.isNotBlank(dto.getBusinessTypeId())
                && !StringUtils.equals(dto.getBusinessTypeId(), parentBusinessTypeId)) {
            throw new IllegalArgumentException("子数据集市必须继承上级数据集市的业务分类");
        }
        dto.setBusinessTypeId(parentBusinessTypeId);
    }

    private DataMartPO validateParent(String parentId, String currentId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        if (StringUtils.equals(parentId, currentId)) {
            throw new IllegalArgumentException("数据集市不能将自身设置为父节点");
        }

        Set<String> visited = new HashSet<>();
        String currentParentId = parentId;
        while (StringUtils.isNotBlank(currentParentId)) {
            if (!visited.add(currentParentId)) {
                throw new IllegalArgumentException("数据集市父级关系已存在循环引用");
            }
            if (StringUtils.equals(currentParentId, currentId)) {
                throw new IllegalArgumentException("不能将数据集市移动到自己的下级节点");
            }
            DataMartPO parent = dataMartDao.queryById(currentParentId);
            if (parent == null) {
                throw new IllegalArgumentException("上级数据集市不存在或不属于当前项目");
            }
            currentParentId = parent.getParentId();
        }
        return dataMartDao.queryById(parentId);
    }

    private void addAllParentIds(String parentId, Set<String> includeIds, Set<String> visited) {
        String currentParentId = parentId;
        while (StringUtils.isNotBlank(currentParentId) && visited.add(currentParentId)) {
            DataMartPO parent = dataMartDao.queryById(currentParentId);
            if (parent == null) {
                return;
            }
            includeIds.add(parent.getId());
            currentParentId = parent.getParentId();
        }
    }

    private List<TreeVO> buildFullTree(LambdaQueryWrapper<DataMartPO> wrapper) {
        return toTree(dataMartDao.queryList(wrapper));
    }

    private List<TreeVO> toTree(List<DataMartPO> records) {
        List<TreeVO> nodes = new ArrayList<>();
        records.forEach(item -> nodes.add(new TreeVO(
                item.getId(),
                item.getId(),
                item.getMartName(),
                item.getParentId(),
                null)));
        return new TreeUtil<TreeVO>().getTreeByList(nodes);
    }
}
