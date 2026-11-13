package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.ComposeIndicatorDao;
import io.yak.ops.dao.repository.modeling.DerivativeIndicatorDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.application.service.DwLayerService;
import io.yak.ops.modeling.support.ConvertUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DwLayerServiceImpl extends BaseServiceImpl implements DwLayerService {

    @Resource
    private DwLayerDao dwLayerDao;

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Resource
    private DerivativeIndicatorDao derivativeIndicatorDao;

    @Resource
    private ComposeIndicatorDao composeIndicatorDao;

    @Resource
    private ModifierDao modifierDao;

    @Override
    public String create(DwLayerDTO dto) {
        return create(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DwLayerDTO dto, String operator) {
        normalizeAndValidate(dto);
        validateUniqueShortName(dto.getEnShortName(), null);

        DwLayerPO po = ConvertUtil.sourceToTarget(dto, DwLayerPO.class);
        po.setProjectCode(null);
        po.initInsert();
        po.setCreateBy(resolveOperator(operator));
        po.setUpdateBy(resolveOperator(operator));
        dwLayerDao.insert(po);
        return po.getId();
    }

    @Override
    public DwLayerVO updateById(DwLayerDTO dto) {
        return updateById(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DwLayerVO updateById(DwLayerDTO dto, String operator) {
        if (dto == null || StringUtils.isBlank(dto.getId())) {
            throw new IllegalArgumentException("数仓分层ID不能为空");
        }

        DwLayerPO existing = dwLayerDao.queryById(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("数仓分层不存在或不属于当前项目");
        }
        if (dto.getDwType() != null && dto.getDwType() != existing.getDwType()) {
            throw new IllegalArgumentException("数仓分层类型创建后不可修改");
        }
        if (dto.getDwModelType() != null && dto.getDwModelType() != existing.getDwModelType()) {
            throw new IllegalArgumentException("数仓模型类型创建后不可修改");
        }

        dto.setDwType(existing.getDwType());
        dto.setDwModelType(existing.getDwModelType());
        normalizeAndValidate(dto);
        validateUniqueShortName(dto.getEnShortName(), dto.getId());

        DwLayerPO po = ConvertUtil.sourceToTarget(dto, DwLayerPO.class);
        po.setId(existing.getId());
        po.setProjectCode(null);
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        po.setUpdateBy(resolveOperator(operator));
        if (!dwLayerDao.updateById(po)) {
            throw new IllegalArgumentException("数仓分层更新失败");
        }
        return ConvertUtil.sourceToTarget(po, DwLayerVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("数仓分层ID不能为空");
        }
        if (dwLayerDao.queryById(id) == null) {
            throw new IllegalArgumentException("数仓分层不存在或不属于当前项目");
        }

        long dimensionTableCount = dimensionTableDao.queryCount(
                new LambdaQueryWrapper<DimensionTablePO>().eq(DimensionTablePO::getDwhId, id));
        long derivativeIndicatorCount = derivativeIndicatorDao.queryCount(
                new LambdaQueryWrapper<DerivativeIndicatorPO>().eq(DerivativeIndicatorPO::getDwhId, id));
        long composeIndicatorCount = composeIndicatorDao.queryCount(
                new LambdaQueryWrapper<ComposeIndicatorPO>().eq(ComposeIndicatorPO::getDwhId, id));
        long modifierCount = modifierDao.queryCount(
                new LambdaQueryWrapper<ModifierPO>().eq(ModifierPO::getDwhId, id));

        if (dimensionTableCount + derivativeIndicatorCount + composeIndicatorCount + modifierCount > 0) {
            throw new IllegalArgumentException(
                    "该数仓分层已被引用，不能删除（维度模型 " + dimensionTableCount
                            + " 条、派生指标 " + derivativeIndicatorCount
                            + " 条、复合指标 " + composeIndicatorCount
                            + " 条、指标修饰词 " + modifierCount + " 条）");
        }
        if (!dwLayerDao.deleteById(id)) {
            throw new IllegalArgumentException("数仓分层删除失败");
        }
        return id;
    }

    @Override
    public DwLayerVO selectById(String id) {
        DwLayerPO po = dwLayerDao.queryById(id);
        if (po == null) {
            throw new IllegalArgumentException("数仓分层不存在或不属于当前项目");
        }
        return ConvertUtil.sourceToTarget(po, DwLayerVO.class);
    }

    @Override
    public DwLayerVO selectByDwType(String dwType) {
        if (StringUtils.isBlank(dwType)) {
            return null;
        }

        DwTypeEnum layerType;
        try {
            layerType = DwTypeEnum.valueOf(dwType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的数仓分层类型: " + dwType, ex);
        }

        LambdaQueryWrapper<DwLayerPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DwLayerPO::getDwType, layerType);
        if (layerType == DwTypeEnum.PUBLIC_LAYER) {
            wrapper.eq(DwLayerPO::getDwModelType, DwModelEnum.DETAIL);
        }
        wrapper.orderByAsc(DwLayerPO::getEnShortName)
                .orderByAsc(DwLayerPO::getId)
                .last("limit 1");

        List<DwLayerPO> layers = dwLayerDao.queryList(wrapper);
        return layers.isEmpty() ? null : ConvertUtil.sourceToTarget(layers.get(0), DwLayerVO.class);
    }

    @Override
    public IPage<DwLayerVO> paging(DwLayerDTO dto) {
        IPage<DwLayerPO> iPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        LambdaQueryWrapper<DwLayerPO> wrapper = new LambdaQueryWrapper<>();
        if (dto.getDwType() != null) {
            wrapper.eq(DwLayerPO::getDwType, dto.getDwType());
        }
        wrapper.orderByDesc(DwLayerPO::getUpdateTime);
        IPage<DwLayerPO> result = dwLayerDao.queryPage(iPage, wrapper);
        List<DwLayerVO> records = ConvertUtil.sourceListToTarget(result.getRecords(), DwLayerVO.class);
        Page<DwLayerVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
    }

    @Override
    public List<DwLayerVO> all(DwLayerDTO dto) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (dto != null && dto.getDwType() != null) {
            queryWrapper.eq(DwLayerPO::getDwType, dto.getDwType());
        }
        queryWrapper.orderByAsc(DwLayerPO::getDwType)
                .orderByAsc(DwLayerPO::getEnShortName)
                .orderByAsc(DwLayerPO::getId);
        return dwLayerDao.queryList(queryWrapper).stream()
                .map(item -> ConvertUtil.sourceToTarget(item, DwLayerVO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<OptionVO> option(List<String> modelTypes) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (CollectionUtils.isNotEmpty(modelTypes)) {
            queryWrapper.in(DwLayerPO::getDwModelType, modelTypes);
        }
        queryWrapper.orderByAsc(DwLayerPO::getEnShortName);
        return convert(queryWrapper);
    }

    @Override
    public List<OptionVO> optionByModalType(DwModelEnum modalType) {
        LambdaQueryWrapper<DwLayerPO> queryWrapper = new LambdaQueryWrapper<>();
        if (modalType != null) {
            queryWrapper.eq(DwLayerPO::getDwModelType, modalType);
        }
        queryWrapper.orderByAsc(DwLayerPO::getEnShortName);
        return convert(queryWrapper);
    }

    private String resolveOperator(String operator) {
        return StringUtils.isBlank(operator) ? "sys" : operator.trim();
    }

    private void normalizeAndValidate(DwLayerDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("数仓分层参数不能为空");
        }

        dto.setEnShortName(StringUtils.upperCase(StringUtils.trimToEmpty(dto.getEnShortName()), Locale.ROOT));
        dto.setEnName(StringUtils.trim(dto.getEnName()));
        dto.setCnName(StringUtils.trim(dto.getCnName()));
        dto.setDirector(StringUtils.trim(dto.getDirector()));
        dto.setRemark(StringUtils.trim(dto.getRemark()));

        if (StringUtils.isBlank(dto.getEnShortName())) {
            throw new IllegalArgumentException("英文缩写不能为空");
        }
        if (dto.getEnShortName().length() > 64
                || !dto.getEnShortName().matches("[A-Z0-9][A-Z0-9_]*")) {
            throw new IllegalArgumentException("英文缩写格式不正确");
        }
        if (StringUtils.isBlank(dto.getEnName()) || dto.getEnName().length() > 64) {
            throw new IllegalArgumentException("英文名称不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getCnName()) || dto.getCnName().length() > 64) {
            throw new IllegalArgumentException("中文名称不能为空且不能超过64个字符");
        }
        if (StringUtils.isBlank(dto.getDirector()) || dto.getDirector().length() > 32) {
            throw new IllegalArgumentException("负责人不能为空且不能超过32个字符");
        }
        if (dto.getRemark() != null && dto.getRemark().length() > 2048) {
            throw new IllegalArgumentException("备注不能超过2048个字符");
        }
        if (dto.getDwType() == null) {
            throw new IllegalArgumentException("数仓分层类型不能为空");
        }

        if (dto.getDwType() == DwTypeEnum.NONE_LAYER) {
            if (dto.getDwModelType() != null) {
                throw new IllegalArgumentException("未分层无需指定模型类型");
            }
        } else if (!DwModelEnum.canCreate(dto.getDwType(), dto.getDwModelType())) {
            throw new IllegalArgumentException("当前数仓分层不支持所选模型类型");
        }
    }

    private void validateUniqueShortName(String enShortName, String excludeId) {
        QueryWrapper<DwLayerPO> wrapper = new QueryWrapper<>();
        wrapper.apply("UPPER(en_short_name) = {0}", enShortName);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (dwLayerDao.queryCount(wrapper) > 0) {
            throw new IllegalArgumentException("英文缩写已存在: " + enShortName);
        }
    }

    private List<OptionVO> convert(LambdaQueryWrapper<DwLayerPO> queryWrapper) {
        return dwLayerDao.queryList(queryWrapper).stream().map(item -> {
            OptionVO optionVO = new OptionVO();
            DwLayerVO vo = ConvertUtil.sourceToTarget(item, DwLayerVO.class);
            optionVO.setLabel(vo);
            optionVO.setValue(item.getId());
            return optionVO;
        }).collect(Collectors.toList());
    }
}
