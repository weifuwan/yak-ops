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
import io.yak.ops.modeling.api.dto.datadomain.DataDomainDTO;
import io.yak.ops.modeling.api.vo.datadomain.DataDomainVO;
import io.yak.ops.modeling.application.service.BusinessProcessService;
import io.yak.ops.modeling.application.service.DataDomainService;
import io.yak.ops.modeling.support.ConvertUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DataDomainServiceImpl extends BaseServiceImpl implements DataDomainService {

    @Resource
    private BusinessProcessService businessProcessService;

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
    public String create(DataDomainDTO dto) {
        return create(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DataDomainDTO dto, String operator) {
        normalizeAndValidate(dto);
        validateUniqueShortName(dto.getEnShortName(), null);

        DataDomainPO po = ConvertUtil.sourceToTarget(dto, DataDomainPO.class);
        po.initInsert();
        po.setCreateBy(StringUtils.defaultIfBlank(operator, "sys"));
        po.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        po.setFlag(0);
        dataDomainDao.insert(po);

        BusinessProcessDTO defaultProcess = BusinessProcessDTO.builder()
                .cnName(appendSuffix(dto.getCnName(), "_默认", 64))
                .enName(appendSuffix(dto.getEnName(), "_default", 64))
                .enShortName(appendSuffix(dto.getEnShortName(), "_default", 64))
                .director(dto.getDirector())
                .remark(String.format("%s下的默认业务过程", dto.getCnName()))
                .flag(1)
                .dataDomainId(po.getId())
                .build();
        businessProcessService.create(defaultProcess, operator);
        return po.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataDomainVO updateById(DataDomainDTO dto) {
        return updateById(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataDomainVO updateById(DataDomainDTO dto, String operator) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("数据域ID不能为空");
        }

        DataDomainPO existing = dataDomainDao.queryById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("数据域不存在或不属于当前项目");
        }
        if (existing.getFlag() == 1) {
            dto.setEnShortName(existing.getEnShortName());
            dto.setEnName(existing.getEnName());
            dto.setCnName(existing.getCnName());
        }

        normalizeAndValidate(dto);
        validateUniqueShortName(dto.getEnShortName(), existing.getId());

        DataDomainPO po = ConvertUtil.sourceToTarget(dto, DataDomainPO.class);
        po.setId(existing.getId());
        po.setFlag(existing.getFlag());
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        po.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        if (!dataDomainDao.updateById(po)) {
            throw new IllegalArgumentException("数据域更新失败");
        }
        return ConvertUtil.sourceToTarget(dataDomainDao.queryById(existing.getId()), DataDomainVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("数据域ID不能为空");
        }
        DataDomainPO existing = dataDomainDao.queryById(id);
        if (existing == null) {
            throw new IllegalArgumentException("数据域不存在或不属于当前项目");
        }
        if (existing.getFlag() == 1) {
            throw new IllegalArgumentException("系统默认数据域不能删除");
        }

        List<BusinessProcessPO> processes = businessProcessDao.queryList(
                new LambdaQueryWrapper<BusinessProcessPO>().eq(BusinessProcessPO::getDataDomainId, id));
        List<String> processIds = processes.stream()
                .map(BusinessProcessPO::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        LambdaQueryWrapper<DimensionTablePO> referenceWrapper = new LambdaQueryWrapper<>();
        if (processIds.isEmpty()) {
            referenceWrapper.eq(DimensionTablePO::getDomainId, id);
        } else {
            referenceWrapper.and(wrapper -> wrapper
                    .eq(DimensionTablePO::getDomainId, id)
                    .or()
                    .in(DimensionTablePO::getBusProcessId, processIds));
        }
        long referenceCount = dimensionTableDao.queryCount(referenceWrapper);
        if (referenceCount > 0) {
            throw new IllegalArgumentException(
                    "该数据域或其业务过程已被维度模型引用（" + referenceCount + "条），不能删除");
        }

        businessProcessService.deleteByDomainId(id);
        businessTypeRelatedDataDomainDao.deleteByWrapper(
                new LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO>()
                        .eq(BusinessTypeRelatedDataDomainPO::getDataId, id));
        if (!dataDomainDao.deleteById(id)) {
            throw new IllegalArgumentException("数据域删除失败");
        }
        return id;
    }

    @Override
    public DataDomainVO selectById(String id) {
        DataDomainPO po = dataDomainDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("数据域不存在或不属于当前项目");
        }
        return ConvertUtil.sourceToTarget(po, DataDomainVO.class);
    }

    @Override
    public IPage<DataDomainVO> paging(DataDomainDTO dto) {
        IPage<DataDomainPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(DataDomainPO::getUpdateTime);
        IPage<DataDomainPO> result = dataDomainDao.queryPage(iPage, wrapper);
        List<DataDomainVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DataDomainVO.class);
        Page<DataDomainVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
    }

    @Override
    public List<DataDomainVO> all() {
        LambdaQueryWrapper<DataDomainPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DataDomainPO::getEnShortName).orderByAsc(DataDomainPO::getId);
        return dataDomainDao.queryList(wrapper).stream()
                .map(item -> ConvertUtil.sourceToTarget(item, DataDomainVO.class))
                .collect(Collectors.toList());
    }

    private void normalizeAndValidate(DataDomainDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("数据域参数不能为空");
        }
        dto.setEnShortName(StringUtils.lowerCase(StringUtils.trimToEmpty(dto.getEnShortName()), Locale.ROOT));
        dto.setEnName(StringUtils.trim(dto.getEnName()));
        dto.setCnName(StringUtils.trim(dto.getCnName()));
        dto.setReviewer(StringUtils.trim(dto.getReviewer()));
        dto.setDirector(StringUtils.trim(dto.getDirector()));
        dto.setRemark(StringUtils.trim(dto.getRemark()));

        if (StringUtils.isBlank(dto.getEnShortName())
                || dto.getEnShortName().length() > 64
                || !dto.getEnShortName().matches("[a-z0-9](?:[a-z0-9_]*[a-z0-9])?")) {
            throw new IllegalArgumentException("数据域英文缩写格式不正确");
        }
        if (StringUtils.isBlank(dto.getEnName()) || dto.getEnName().length() > 64) {
            throw new IllegalArgumentException("数据域英文名不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getCnName()) || dto.getCnName().length() > 64) {
            throw new IllegalArgumentException("数据域中文名不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getDirector()) || dto.getDirector().length() > 32) {
            throw new IllegalArgumentException("数据域负责人不能为空且不能超过32个字符");
        }
        if (dto.getReviewer() != null && dto.getReviewer().length() > 255) {
            throw new IllegalArgumentException("模型评审人不能超过255个字符");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 2048) {
            throw new IllegalArgumentException("备注不能超过2048个字符");
        }
    }

    private void validateUniqueShortName(String shortName, String excludeId) {
        QueryWrapper<DataDomainPO> wrapper = new QueryWrapper<>();
        wrapper.apply("LOWER(en_short_name) = {0}", shortName.toLowerCase(Locale.ROOT));
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (dataDomainDao.queryCount(wrapper) > 0) {
            throw new IllegalArgumentException("数据域英文缩写已存在：" + shortName);
        }
    }

    private String appendSuffix(String value, String suffix, int maxLength) {
        String base = StringUtils.defaultString(value);
        int allowedLength = Math.max(0, maxLength - suffix.length());
        return StringUtils.left(base, allowedLength) + suffix;
    }
}
