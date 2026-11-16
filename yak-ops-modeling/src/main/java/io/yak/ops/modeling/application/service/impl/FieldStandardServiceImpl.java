package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.FieldStandardPO;
import io.yak.ops.modeling.api.dto.fieldstandard.FieldStandardDTO;
import io.yak.ops.modeling.api.vo.fieldstandard.FieldStandardVO;
import io.yak.ops.modeling.application.service.FieldStandardService;
import org.springframework.stereotype.Service;

/** Field-standard page business service. */
@Service
public class FieldStandardServiceImpl
        extends AbstractModelingAssetService<FieldStandardPO, FieldStandardDTO, FieldStandardVO>
        implements FieldStandardService {

    @Override
    protected String resourceKey() {
        return "field-standard";
    }

    @Override
    protected Class<FieldStandardPO> entityClass() {
        return FieldStandardPO.class;
    }

    @Override
    protected Class<FieldStandardDTO> dtoClass() {
        return FieldStandardDTO.class;
    }

    @Override
    protected Class<FieldStandardVO> viewClass() {
        return FieldStandardVO.class;
    }

    @Override
    protected QueryWrapper<FieldStandardPO> buildQueryWrapper(FieldStandardDTO query) {
        QueryWrapper<FieldStandardPO> wrapper = new QueryWrapper<FieldStandardPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name", "std_code");
        eqIfNotBlank(wrapper, query.getParentId(), "parent_id");
        return wrapper;
    }

    @Override
    protected void validate(FieldStandardDTO dto) {
        requireFields(dto, "cnName", "stdCode", "enShortName", "enName", "fieldDataType");
    }

    @Override
    protected void beforeCreate(FieldStandardDTO dto) {
        validateDirectoryModule(dto.getParentId(), "FIELD");
    }

    @Override
    protected void beforeUpdate(FieldStandardDTO dto, FieldStandardPO existing) {
        validateDirectoryModule(dto.getParentId(), "FIELD");
    }
}
