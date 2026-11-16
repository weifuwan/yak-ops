package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.DictionaryStandardPO;
import io.yak.ops.modeling.api.dto.namingdictionary.NamingDictionaryDTO;
import io.yak.ops.modeling.api.vo.namingdictionary.NamingDictionaryVO;
import io.yak.ops.modeling.application.service.NamingDictionaryService;
import org.springframework.stereotype.Service;

/** Naming-dictionary page business service. */
@Service
public class NamingDictionaryServiceImpl
        extends AbstractModelingAssetService<DictionaryStandardPO, NamingDictionaryDTO, NamingDictionaryVO>
        implements NamingDictionaryService {

    @Override
    protected String resourceKey() {
        return "dictionary-standard";
    }

    @Override
    protected Class<DictionaryStandardPO> entityClass() {
        return DictionaryStandardPO.class;
    }

    @Override
    protected Class<NamingDictionaryDTO> dtoClass() {
        return NamingDictionaryDTO.class;
    }

    @Override
    protected Class<NamingDictionaryVO> viewClass() {
        return NamingDictionaryVO.class;
    }

    @Override
    protected QueryWrapper<DictionaryStandardPO> buildQueryWrapper(NamingDictionaryDTO query) {
        QueryWrapper<DictionaryStandardPO> wrapper = new QueryWrapper<DictionaryStandardPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        return wrapper;
    }

    @Override
    protected void validate(NamingDictionaryDTO dto) {
        requireFields(dto, "cnName", "enShortName", "enName");
    }
}
