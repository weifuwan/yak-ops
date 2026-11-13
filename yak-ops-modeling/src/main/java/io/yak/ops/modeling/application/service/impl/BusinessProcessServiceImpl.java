package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.modeling.api.dto.businessprocess.BusinessProcessDTO;
import io.yak.ops.modeling.api.vo.businessprocess.BusinessProcessVO;
import io.yak.ops.modeling.api.vo.businessprocess.CascaderTreeVO;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import io.yak.ops.modeling.support.ConvertUtil;
import io.yak.ops.modeling.support.TreeUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(BusinessProcessDTO dto) {
        normalizeAndValidate(dto, null);
        validateUniqueShortName(dto.getDataDomainId(), dto.getEnShortName(), null);

        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.initInsert();
        po.setFlag(dto.getFlag() == null ? 0 : dto.getFlag());
        businessProcessDao.insert(po);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO updateById(BusinessProcessDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("业务过程ID不能为空");
        }
        BusinessProcessPO existing = businessProcessDao.queryById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("业务过程不存在或不属于当前项目");
        }

        if (existing.getFlag() == 1) {
            dto.setEnShortName(existing.getEnShortName());
            dto.setEnName(existing.getEnName());
            dto.setCnName(existing.getCnName());
            dto.setDataDomainId(existing.getDataDomainId());
            dto.setRemark(existing.getRemark());
            dto.setFlag(1);
        }
        normalizeAndValidate(dto, existing.getId());
        validateUniqueShortName(dto.getDataDomainId(), dto.getEnShortName(), existing.getId());

        BusinessProcessPO po = ConvertUtil.sourceToTarget(dto, BusinessProcessPO.class);
        po.setId(existing.getId());
        po.setFlag(existing.getFlag());
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        if (!businessProcessDao.updateById(po)) {
            throw new IllegalArgumentException("业务过程更新失败");
        }
        return selectById(existing.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("业务过程ID不能为空");
        }
        BusinessProcessPO existing = businessProcessDao.queryById(id);
        if (existing == null) {
            throw new IllegalArgumentException("业务过程不存在或不属于当前项目");
        }
        if (existing.getFlag() == 1) {
            throw new IllegalArgumentException("系统默认业务过程不能删除");
        }
        long references = dimensionTableDao.queryCount(
                new LambdaQueryWrapper<DimensionTablePO>().eq(DimensionTablePO::getBusProcessId, id));
        if (references > 0) {
            throw new IllegalArgumentException("该业务过程已被维度模型引用（" + references + "条），不能删除");
        }
        if (!businessProcessDao.deleteById(id)) {
            throw new IllegalArgumentException("业务过程删除失败");
        }
        return id;
    }

    @Override
    public BusinessProcessVO selectById(String id) {
        BusinessProcessPO po = businessProcessDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("业务过程不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(po.getDataDomainId())) {
            throw new IllegalArgumentException("业务过程未关联数据域");
        }
        DataDomainPO domain = dataDomainDao.queryById(po.getDataDomainId());
        if (domain == null) {
            throw new IllegalArgumentException("业务过程关联的数据域不存在");
        }
        BusinessProcessVO vo = ConvertUtil.sourceToTarget(po, BusinessProcessVO.class);
        vo.setDataDomainCnName(domain.getCnName());
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
        LambdaQueryWrapper<BusinessProcessPO> wrapper = new LambdaQueryWrapper<>();
        if (dto != null && StringUtils.isNotBlank(dto.getDataDomainId())) {
            wrapper.eq(BusinessProcessPO::getDataDomainId, dto.getDataDomainId());
        }
        wrapper.orderByDesc(BusinessProcessPO::getFlag).orderByAsc(BusinessProcessPO::getEnShortName);
        return businessProcessDao.queryList(wrapper).stream()
                .map(item -> ConvertUtil.sourceToTarget(item, BusinessProcessVO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDomainId(String dataDomainId) {
        if (StringUtils.isBlank(dataDomainId)) {
            throw new IllegalArgumentException("数据域ID不能为空");
        }
        LambdaQueryWrapper<BusinessProcessPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessProcessPO::getDataDomainId, dataDomainId);
        List<BusinessProcessPO> processes = businessProcessDao.queryList(wrapper);
        List<String> ids = processes.stream()
                .map(BusinessProcessPO::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            long references = dimensionTableDao.queryCount(
                    new LambdaQueryWrapper<DimensionTablePO>().in(DimensionTablePO::getBusProcessId, ids));
            if (references > 0) {
                throw new IllegalArgumentException("该数据域下的业务过程已被维度模型引用（" + references + "条），不能删除");
            }
        }
        businessProcessDao.deleteByWrapper(wrapper);
    }

    @Override
    public List<CascaderTreeVO> treeList(String busTypeId) {
        List<BusinessProcessPO> processes = getFilteredBusinessProcessList(busTypeId);
        if (CollectionUtils.isEmpty(processes)) {
            return new ArrayList<>();
        }
        return buildCascaderTreeResult(processes);
    }

    private List<BusinessProcessPO> getFilteredBusinessProcessList(String busTypeId) {
        LambdaQueryWrapper<BusinessProcessPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BusinessProcessPO::getDataDomainId);
        if (StringUtils.isBlank(busTypeId)) {
            return businessProcessDao.queryList(wrapper);
        }

        Set<String> domainIds = getDataDomainIdsByBusTypeId(busTypeId);
        if (CollectionUtils.isEmpty(domainIds)) {
            return new ArrayList<>();
        }
        wrapper.in(BusinessProcessPO::getDataDomainId, domainIds);
        return businessProcessDao.queryList(wrapper);
    }

    private Set<String> getDataDomainIdsByBusTypeId(String busTypeId) {
        LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, busTypeId);
        return businessTypeRelatedDataDomainDao.queryList(wrapper).stream()
                .map(BusinessTypeRelatedDataDomainPO::getDataId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private List<CascaderTreeVO> buildCascaderTreeResult(List<BusinessProcessPO> processes) {
        Map<String, List<BusinessProcessPO>> processMap = processes.stream()
                .filter(item -> StringUtils.isNotBlank(item.getDataDomainId()))
                .collect(Collectors.groupingBy(BusinessProcessPO::getDataDomainId));

        List<DataDomainPO> domains = getDataDomainList(processMap.keySet());
        List<CascaderTreeVO> nodes = new ArrayList<>();
        domains.forEach(domain -> {
            nodes.add(new CascaderTreeVO(domain.getId(), null, domain.getCnName(), domain.getId(), null));
            processMap.getOrDefault(domain.getId(), new ArrayList<>()).forEach(process ->
                    nodes.add(new CascaderTreeVO(
                            process.getId(),
                            process.getDataDomainId(),
                            process.getCnName(),
                            process.getId(),
                            null)));
        });
        return new TreeUtil<CascaderTreeVO>().getTreeByList(nodes);
    }

    private List<DataDomainPO> getDataDomainList(Set<String> dataDomainIds) {
        if (CollectionUtils.isEmpty(dataDomainIds)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DataDomainPO::getId, dataDomainIds);
        wrapper.orderByAsc(DataDomainPO::getEnShortName);
        return dataDomainDao.queryList(wrapper);
    }

    private void normalizeAndValidate(BusinessProcessDTO dto, String excludeId) {
        if (dto == null) {
            throw new IllegalArgumentException("业务过程参数不能为空");
        }
        dto.setEnShortName(StringUtils.lowerCase(StringUtils.trimToEmpty(dto.getEnShortName()), Locale.ROOT));
        dto.setEnName(StringUtils.trim(dto.getEnName()));
        dto.setCnName(StringUtils.trim(dto.getCnName()));
        dto.setDataDomainId(StringUtils.trim(dto.getDataDomainId()));
        dto.setDirector(StringUtils.trim(dto.getDirector()));
        dto.setRemark(StringUtils.trim(dto.getRemark()));

        if (StringUtils.isBlank(dto.getEnShortName())
                || dto.getEnShortName().length() > 64
                || !dto.getEnShortName().matches("[a-z0-9](?:[a-z0-9_]*[a-z0-9])?")) {
            throw new IllegalArgumentException("业务过程英文缩写格式不正确");
        }
        if (StringUtils.isBlank(dto.getEnName()) || dto.getEnName().length() > 64) {
            throw new IllegalArgumentException("业务过程英文名不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getCnName()) || dto.getCnName().length() > 64) {
            throw new IllegalArgumentException("业务过程中文名不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getDataDomainId())) {
            throw new IllegalArgumentException("请选择所属数据域");
        }
        if (dataDomainDao.queryById(dto.getDataDomainId()) == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getDirector()) || dto.getDirector().length() > 32) {
            throw new IllegalArgumentException("业务过程负责人不能为空且不能超过32个字符");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 2048) {
            throw new IllegalArgumentException("备注不能超过2048个字符");
        }
    }

    private void validateUniqueShortName(String dataDomainId, String shortName, String excludeId) {
        QueryWrapper<BusinessProcessPO> wrapper = new QueryWrapper<>();
        wrapper.eq("data_domain_id", dataDomainId)
                .apply("LOWER(en_short_name) = {0}", shortName.toLowerCase(Locale.ROOT));
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (businessProcessDao.queryCount(wrapper) > 0) {
            throw new IllegalArgumentException("该数据域下业务过程英文缩写已存在：" + shortName);
        }
    }
}
