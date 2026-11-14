package io.yak.ops.dao.repository.impl.modeling;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.mapper.DwLayerMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
public class DwLayerDaoImpl extends BaseDao<DwLayerPO, DwLayerMapper> implements DwLayerDao {

    public DwLayerDaoImpl(@NonNull DwLayerMapper mapper) {
        super(mapper);
    }

    @Override
    public boolean existsByEnShortName(String enShortName) {
        QueryWrapper<DwLayerPO> wrapper = new QueryWrapper<>();
        wrapper.apply("UPPER(en_short_name) = {0}", enShortName.toUpperCase(Locale.ROOT));
        return queryCount(wrapper) > 0;
    }

    @Override
    public boolean existsSystemOdsLayer() {
        QueryWrapper<DwLayerPO> wrapper = new QueryWrapper<>();
        wrapper.apply("UPPER(en_short_name) = {0}", "ODS");
        wrapper.eq("create_by", "sys");
        return queryCount(wrapper) > 0;
    }
}
