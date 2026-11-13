package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.modeling.api.vo.datamart.DataMartVO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.api.vo.tree.TreeTypeValueVO;
import io.yak.ops.modeling.application.service.DataMartService;
import io.yak.ops.modeling.application.service.SubjectDomainService;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubjectDomainServiceImpl extends BaseServiceImpl implements SubjectDomainService {

    private static final String MART = "MART";
    private static final String SUBJECT = "SUBJECT";

    @Resource
    private DataMartService dataMartService;

    @Resource
    private SubjectDomainDao subjectDomainDao;

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(SubjectDomainDTO dto) {
        normalizeAndValidate(dto);
        DataMartVO mart = requireDataMart(dto.getDataMartId());
        SubjectDomainPO parent = validateParent(dto.getParentId(), null, mart.getId());
        if (parent != null) {
            dto.setDataMartId(parent.getDataMartId());
        }
        validateUniqueShortName(dto.getDataMartId(), dto.getEnShortName(), null);

        SubjectDomainPO po = ConvertUtil.sourceToTarget(dto, SubjectDomainPO.class);
        po.initInsert();
        subjectDomainDao.insert(po);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectDomainVO updateById(SubjectDomainDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("主题域ID不能为空");
        }
        SubjectDomainPO existing = subjectDomainDao.queryById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("主题域不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getParentId()) && StringUtils.isNotBlank(existing.getParentId())) {
            dto.setParentId(existing.getParentId());
        }
        if (StringUtils.isBlank(dto.getDataMartId())) {
            dto.setDataMartId(existing.getDataMartId());
        }

        normalizeAndValidate(dto);
        requireDataMart(dto.getDataMartId());
        SubjectDomainPO parent = validateParent(dto.getParentId(), existing.getId(), dto.getDataMartId());
        if (parent != null) {
            dto.setDataMartId(parent.getDataMartId());
        }

        long childCount = subjectDomainDao.queryCount(
                new LambdaQueryWrapper<SubjectDomainPO>().eq(SubjectDomainPO::getParentId, existing.getId()));
        if (childCount > 0 && !StringUtils.equals(existing.getDataMartId(), dto.getDataMartId())) {
            throw new IllegalArgumentException("该主题域下存在子主题域，不能修改所属数据集市");
        }
        validateUniqueShortName(dto.getDataMartId(), dto.getEnShortName(), existing.getId());

        SubjectDomainPO po = ConvertUtil.sourceToTarget(dto, SubjectDomainPO.class);
        po.setId(existing.getId());
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        if (!subjectDomainDao.updateById(po)) {
            throw new IllegalArgumentException("主题域更新失败");
        }
        return selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("主题域ID不能为空");
        }
        if (subjectDomainDao.queryById(id) == null) {
            throw new IllegalArgumentException("主题域不存在或不属于当前项目");
        }
        long childCount = subjectDomainDao.queryCount(
                new LambdaQueryWrapper<SubjectDomainPO>().eq(SubjectDomainPO::getParentId, id));
        if (childCount > 0) {
            throw new IllegalArgumentException("该主题域下存在子主题域（" + childCount + "条），不能删除");
        }
        long dimensionCount = dimensionTableDao.queryCount(
                new LambdaQueryWrapper<DimensionTablePO>().eq(DimensionTablePO::getSubjectId, id));
        if (dimensionCount > 0) {
            throw new IllegalArgumentException("该主题域已被维度模型引用（" + dimensionCount + "条），不能删除");
        }
        if (!subjectDomainDao.deleteById(id)) {
            throw new IllegalArgumentException("主题域删除失败");
        }
        return id;
    }

    @Override
    public SubjectDomainVO selectById(String id) {
        SubjectDomainPO po = subjectDomainDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("主题域不存在或不属于当前项目");
        }
        SubjectDomainVO vo = ConvertUtil.sourceToTarget(po, SubjectDomainVO.class);
        DataMartVO mart = requireDataMart(vo.getDataMartId());
        vo.setDataMartName(mart.getMartName());
        return vo;
    }

    @Override
    public IPage<SubjectDomainVO> paging(SubjectDomainDTO dto) {
        IPage<SubjectDomainPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<SubjectDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(dto.getDataMartId()), SubjectDomainPO::getDataMartId, dto.getDataMartId())
                .eq(StringUtils.isNotBlank(dto.getId()), SubjectDomainPO::getParentId, dto.getId())
                .orderByDesc(SubjectDomainPO::getUpdateTime);
        IPage<SubjectDomainPO> result = subjectDomainDao.queryPage(iPage, wrapper);
        List<SubjectDomainVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), SubjectDomainVO.class);
        Page<SubjectDomainVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
    }

    @Override
    public List<TreeTypeValueVO> tree() {
        List<TreeTypeValueVO> nodes = new ArrayList<>();
        dataMartService.all().forEach(item -> {
            String id = item.getId() + "-" + MART;
            String parentId = StringUtils.isNotBlank(item.getParentId()) ? item.getParentId() + "-" + MART : null;
            nodes.add(new TreeTypeValueVO(id, item.getMartName(), parentId, MART, null, id));
        });
        all().forEach(item -> {
            String id = item.getId() + "-" + SUBJECT;
            String parentId = determineParentId(item);
            nodes.add(new TreeTypeValueVO(id, item.getDomainName(), parentId, SUBJECT, null, id));
        });
        return new TreeUtil<TreeTypeValueVO>().getTreeByList(nodes);
    }

    private String determineParentId(SubjectDomainVO item) {
        if (StringUtils.isNotBlank(item.getParentId())) {
            return item.getParentId() + "-" + SUBJECT;
        }
        return item.getDataMartId() + "-" + MART;
    }

    @Override
    public List<SubjectDomainVO> all() {
        LambdaQueryWrapper<SubjectDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SubjectDomainPO::getDataMartId)
                .orderByAsc(SubjectDomainPO::getParentId)
                .orderByAsc(SubjectDomainPO::getEnShortName);
        return subjectDomainDao.queryList(wrapper).stream()
                .map(item -> ConvertUtil.sourceToTarget(item, SubjectDomainVO.class))
                .collect(Collectors.toList());
    }

    private void normalizeAndValidate(SubjectDomainDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("主题域参数不能为空");
        }
        dto.setEnShortName(StringUtils.lowerCase(StringUtils.trimToEmpty(dto.getEnShortName()), Locale.ROOT));
        dto.setDomainName(StringUtils.trim(dto.getDomainName()));
        dto.setDirector(StringUtils.trim(dto.getDirector()));
        dto.setDataMartId(StringUtils.trim(dto.getDataMartId()));
        dto.setParentId(StringUtils.trim(dto.getParentId()));
        dto.setRemark(StringUtils.trim(dto.getRemark()));

        if (StringUtils.isBlank(dto.getEnShortName()) || dto.getEnShortName().length() > 64
                || !dto.getEnShortName().matches("[a-z0-9](?:[a-z0-9_]*[a-z0-9])?")) {
            throw new IllegalArgumentException("主题域英文缩写格式不正确");
        }
        if (StringUtils.isBlank(dto.getDomainName()) || dto.getDomainName().length() > 64) {
            throw new IllegalArgumentException("主题域名称不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getDirector()) || dto.getDirector().length() > 32) {
            throw new IllegalArgumentException("主题域负责人不能为空且不能超过32个字符");
        }
        if (StringUtils.isBlank(dto.getDataMartId())) {
            throw new IllegalArgumentException("所属数据集市不能为空");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 2048) {
            throw new IllegalArgumentException("备注不能超过2048个字符");
        }
    }

    private DataMartVO requireDataMart(String dataMartId) {
        if (StringUtils.isBlank(dataMartId)) {
            throw new IllegalArgumentException("所属数据集市不能为空");
        }
        return dataMartService.selectById(dataMartId);
    }

    private SubjectDomainPO validateParent(String parentId, String currentId, String dataMartId) {
        if (StringUtils.isBlank(parentId)) {
            return null;
        }
        if (StringUtils.equals(parentId, currentId)) {
            throw new IllegalArgumentException("主题域不能将自身设置为父节点");
        }
        Set<String> visited = new HashSet<>();
        String currentParentId = parentId;
        while (StringUtils.isNotBlank(currentParentId)) {
            if (!visited.add(currentParentId)) {
                throw new IllegalArgumentException("主题域父级关系已存在循环引用");
            }
            if (StringUtils.equals(currentParentId, currentId)) {
                throw new IllegalArgumentException("不能将主题域移动到自己的下级节点");
            }
            SubjectDomainPO parent = subjectDomainDao.queryById(currentParentId);
            if (parent == null) {
                throw new IllegalArgumentException("上级主题域不存在或不属于当前项目");
            }
            if (!StringUtils.equals(parent.getDataMartId(), dataMartId)) {
                throw new IllegalArgumentException("父主题域与当前主题域必须属于同一个数据集市");
            }
            currentParentId = parent.getParentId();
        }
        return subjectDomainDao.queryById(parentId);
    }

    private void validateUniqueShortName(String dataMartId, String shortName, String excludeId) {
        QueryWrapper<SubjectDomainPO> wrapper = new QueryWrapper<>();
        wrapper.eq("data_mart_id", dataMartId)
                .apply("LOWER(en_short_name) = {0}", shortName.toLowerCase(Locale.ROOT));
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (subjectDomainDao.queryCount(wrapper) > 0) {
            throw new IllegalArgumentException("该数据集市下主题域英文缩写已存在：" + shortName);
        }
    }
}
