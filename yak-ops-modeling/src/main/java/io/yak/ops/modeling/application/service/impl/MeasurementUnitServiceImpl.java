package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.StandardDirectoryPO;
import io.yak.ops.dao.entity.UnitStandardPO;
import io.yak.ops.modeling.api.dto.measurementunit.MeasurementUnitDTO;
import io.yak.ops.modeling.api.vo.measurementunit.MeasurementUnitVO;
import io.yak.ops.modeling.application.service.MeasurementUnitService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Measurement-unit page business service. */
@Service
public class MeasurementUnitServiceImpl
        extends AbstractModelingAssetService<UnitStandardPO, MeasurementUnitDTO, MeasurementUnitVO>
        implements MeasurementUnitService {

    @Override
    protected String resourceKey() {
        return "unit-standard";
    }

    @Override
    protected Class<UnitStandardPO> entityClass() {
        return UnitStandardPO.class;
    }

    @Override
    protected Class<MeasurementUnitDTO> dtoClass() {
        return MeasurementUnitDTO.class;
    }

    @Override
    protected Class<MeasurementUnitVO> viewClass() {
        return MeasurementUnitVO.class;
    }

    @Override
    protected QueryWrapper<UnitStandardPO> buildQueryWrapper(MeasurementUnitDTO query) {
        QueryWrapper<UnitStandardPO> wrapper = new QueryWrapper<UnitStandardPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getParentId(), "parent_id");
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<MeasurementUnitVO> page(MeasurementUnitDTO query) {
        ensureDefaultDirectories();
        return super.page(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MeasurementUnitVO> list(MeasurementUnitDTO query) {
        ensureDefaultDirectories();
        return super.list(query);
    }

    @Override
    protected void validate(MeasurementUnitDTO dto) {
        requireFields(dto, "cnName", "enShortName", "enName");
    }

    @Override
    protected void beforeCreate(MeasurementUnitDTO dto) {
        validateDirectoryModule(dto.getParentId(), "UNIT");
    }

    @Override
    protected void beforeUpdate(MeasurementUnitDTO dto, UnitStandardPO existing) {
        validateDirectoryModule(dto.getParentId(), "UNIT");
    }

    @Override
    protected void beforeDelete(UnitStandardPO existing) {
        if (modelingAssetDao.queryCount("atom-indicator",
                new QueryWrapper<AtomIndicatorPO>().eq("unit_id", existing.getId())) > 0) {
            throw new IllegalArgumentException("该度量单位已被原子指标引用，不能删除");
        }
    }

    private void ensureDefaultDirectories() {
        if (modelingAssetDao.queryCount("standard-directory",
                new QueryWrapper<StandardDirectoryPO>().eq("dir_module", "UNIT")) > 0) {
            return;
        }
        for (String name : new String[]{"货币单位", "对象量词", "比例单位", "排名单位", "时间单位"}) {
            StandardDirectoryPO item = new StandardDirectoryPO();
            item.setDirName(name);
            item.setDirType("FOLDER");
            item.setDirModule("UNIT");
            item.setProjectCode(null);
            item.initInsert();
            modelingAssetDao.insert("standard-directory", item);
        }
    }
}
