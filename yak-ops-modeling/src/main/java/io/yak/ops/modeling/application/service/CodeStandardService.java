package io.yak.ops.modeling.application.service;

import io.yak.ops.modeling.api.dto.codestandard.CodeStandardDTO;
import io.yak.ops.modeling.api.vo.codestandard.CodeStandardVO;
import java.util.List;
import io.yak.ops.modeling.api.dto.codestandard.CodeDetailDTO;
import io.yak.ops.modeling.api.vo.codestandard.CodeDetailVO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;

/** Typed application contract for CodeStandard. */
public interface CodeStandardService extends ModelingCrudService<CodeStandardDTO, CodeStandardVO> {

    List<CodeDetailVO> listDetails(String codeId);
    String createDetail(String codeId, CodeDetailDTO dto, String operator);
    CodeDetailVO updateDetail(String codeId, String detailId, CodeDetailDTO dto, String operator);
    String deleteDetail(String codeId, String detailId);
}
