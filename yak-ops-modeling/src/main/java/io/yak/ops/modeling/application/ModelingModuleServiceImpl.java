package io.yak.ops.modeling.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.vo.ModelingBootstrapVO;
import io.yak.ops.modeling.api.vo.ModelingOverviewVO;
import io.yak.ops.modeling.api.vo.ModelingSectionVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Application entry point for the modeling module.
 *
 * <p>Workspace-scoped business capabilities are registered here as each migration phase is completed.
 */
@Service
public class ModelingModuleServiceImpl extends io.yak.ops.common.service.BaseServiceImpl implements ModelingModuleService {

    private static final int LATEST_SUBMITTED_LIMIT = 5;

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Resource
    private ModelingAssetDao modelingAssetDao;

    @Override
    public ModelingBootstrapVO getBootstrap(long projectCode) {
        return new ModelingBootstrapVO(
                projectCode,
                "DATA_MODELING",
                "数据建模",
                Arrays.asList(
                        new ModelingSectionVO("PLANNING", "数仓规划"),
                        new ModelingSectionVO("MODELING", "模型设计"),
                        new ModelingSectionVO("STANDARDS", "数据标准"),
                        new ModelingSectionVO("INDICATORS", "数据指标")));
    }

    @Override
    public ModelingOverviewVO getOverview(long projectCode) {
        long dimension = countModel(DimensionTableTypeEnum.DIMENSION);
        long detail = countModel(DimensionTableTypeEnum.DETAIL);
        long summary = countModel(DimensionTableTypeEnum.SUMMARY);
        long application = countModel(DimensionTableTypeEnum.APPLICATION);
        long modelTotal = dimension + detail + summary + application;

        long atom = modelingAssetDao.queryCount("atom-indicator", new QueryWrapper<AtomIndicatorPO>());
        long derived = modelingAssetDao.queryCount("derivative-indicator", new QueryWrapper<DerivativeIndicatorPO>());
        long composite = modelingAssetDao.queryCount("compose-indicator", new QueryWrapper<ComposeIndicatorPO>());
        long indicatorTotal = atom + derived + composite;

        List<ModelingOverviewVO.LatestSubmittedItem> latestSubmitted = new ArrayList<>();
        latestSubmitted.addAll(latestSubmittedDimensions());
        latestSubmitted.addAll(latestSubmittedAtomIndicators());
        latestSubmitted.addAll(latestSubmittedDerivedIndicators());
        latestSubmitted.addAll(latestSubmittedCompositeIndicators());
        latestSubmitted.sort(Comparator.comparingLong(
                (ModelingOverviewVO.LatestSubmittedItem item) ->
                        item.getUpdateTime() == null ? 0L : item.getUpdateTime().getTime())
                .reversed());
        if (latestSubmitted.size() > LATEST_SUBMITTED_LIMIT) {
            latestSubmitted = new ArrayList<>(latestSubmitted.subList(0, LATEST_SUBMITTED_LIMIT));
        }

        return ModelingOverviewVO.builder()
                .modelStatistics(ModelingOverviewVO.ModelStatistics.builder()
                        .dimension(dimension)
                        .detail(detail)
                        .summary(summary)
                        .application(application)
                        .total(modelTotal)
                        .build())
                .indicatorStatistics(ModelingOverviewVO.IndicatorStatistics.builder()
                        .atom(atom)
                        .derived(derived)
                        .composite(composite)
                        .total(indicatorTotal)
                        .build())
                .totalAssets(modelTotal + indicatorTotal)
                .latestSubmitted(latestSubmitted)
                .build();
    }

    private long countModel(DimensionTableTypeEnum type) {
        return dimensionTableDao.queryCount(
                new QueryWrapper<DimensionTablePO>().eq("table_type", type.getCode()));
    }

    private List<ModelingOverviewVO.LatestSubmittedItem> latestSubmittedDimensions() {
        List<String> types = Arrays.asList(
                DimensionTableTypeEnum.DIMENSION.getCode(),
                DimensionTableTypeEnum.DETAIL.getCode(),
                DimensionTableTypeEnum.SUMMARY.getCode(),
                DimensionTableTypeEnum.APPLICATION.getCode());
        IPage<DimensionTablePO> page = dimensionTableDao.queryPage(
                new Page<DimensionTablePO>(1, LATEST_SUBMITTED_LIMIT),
                new QueryWrapper<DimensionTablePO>()
                        .eq("submit", Boolean.TRUE)
                        .in("table_type", types)
                        .orderByDesc("update_time"));
        List<ModelingOverviewVO.LatestSubmittedItem> result = new ArrayList<>();
        for (DimensionTablePO item : page.getRecords()) {
            DimensionTableTypeEnum type = item.getTableType();
            result.add(ModelingOverviewVO.LatestSubmittedItem.builder()
                    .id(item.getId())
                    .name(firstNonBlank(item.getCnName(), item.getTableName(), item.getEnShortName()))
                    .assetType(type == null ? "MODEL" : type.getCode())
                    .assetTypeLabel(type == null ? "模型" : type.getName())
                    .updateBy(item.getUpdateBy())
                    .updateTime(item.getUpdateTime())
                    .build());
        }
        return result;
    }

    private List<ModelingOverviewVO.LatestSubmittedItem> latestSubmittedAtomIndicators() {
        IPage<AtomIndicatorPO> page = modelingAssetDao.queryPage(
                "atom-indicator",
                new Page<AtomIndicatorPO>(1, LATEST_SUBMITTED_LIMIT),
                new QueryWrapper<AtomIndicatorPO>()
                        .eq("submit", Boolean.TRUE)
                        .orderByDesc("update_time"));
        List<ModelingOverviewVO.LatestSubmittedItem> result = new ArrayList<>();
        for (AtomIndicatorPO item : page.getRecords()) {
            result.add(latestIndicator(
                    item.getId(),
                    firstNonBlank(item.getCnName(), item.getEnName(), item.getEnShortName()),
                    "ATOM_INDICATOR",
                    "原子指标",
                    item.getUpdateBy(),
                    item.getUpdateTime()));
        }
        return result;
    }

    private List<ModelingOverviewVO.LatestSubmittedItem> latestSubmittedDerivedIndicators() {
        IPage<DerivativeIndicatorPO> page = modelingAssetDao.queryPage(
                "derivative-indicator",
                new Page<DerivativeIndicatorPO>(1, LATEST_SUBMITTED_LIMIT),
                new QueryWrapper<DerivativeIndicatorPO>()
                        .eq("submit", Boolean.TRUE)
                        .orderByDesc("update_time"));
        List<ModelingOverviewVO.LatestSubmittedItem> result = new ArrayList<>();
        for (DerivativeIndicatorPO item : page.getRecords()) {
            result.add(latestIndicator(
                    item.getId(),
                    firstNonBlank(item.getCnName(), item.getEnName(), item.getEnShortName()),
                    "DERIVED_INDICATOR",
                    "派生指标",
                    item.getUpdateBy(),
                    item.getUpdateTime()));
        }
        return result;
    }

    private List<ModelingOverviewVO.LatestSubmittedItem> latestSubmittedCompositeIndicators() {
        IPage<ComposeIndicatorPO> page = modelingAssetDao.queryPage(
                "compose-indicator",
                new Page<ComposeIndicatorPO>(1, LATEST_SUBMITTED_LIMIT),
                new QueryWrapper<ComposeIndicatorPO>()
                        .eq("submit", Boolean.TRUE)
                        .orderByDesc("update_time"));
        List<ModelingOverviewVO.LatestSubmittedItem> result = new ArrayList<>();
        for (ComposeIndicatorPO item : page.getRecords()) {
            result.add(latestIndicator(
                    item.getId(),
                    firstNonBlank(item.getCnName(), item.getEnName(), item.getEnShortName()),
                    "COMPOSITE_INDICATOR",
                    "复合指标",
                    item.getUpdateBy(),
                    item.getUpdateTime()));
        }
        return result;
    }

    private ModelingOverviewVO.LatestSubmittedItem latestIndicator(
            String id,
            String name,
            String assetType,
            String assetTypeLabel,
            String updateBy,
            java.util.Date updateTime) {
        return ModelingOverviewVO.LatestSubmittedItem.builder()
                .id(id)
                .name(name)
                .assetType(assetType)
                .assetTypeLabel(assetTypeLabel)
                .updateBy(updateBy)
                .updateTime(updateTime)
                .build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) return value;
        }
        return "未命名资产";
    }
}
