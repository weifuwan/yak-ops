package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.namingdictionary.NamingDictionaryDTO;
import io.yak.ops.modeling.api.vo.namingdictionary.NamingDictionaryVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for NamingDictionary. */
public interface NamingDictionaryService extends ModelingCrudService<NamingDictionaryDTO, NamingDictionaryVO> {
}
