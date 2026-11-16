package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.CodeDetailPO;
import io.yak.ops.dao.entity.CodeStandardPO;
import io.yak.ops.dao.entity.FieldStandardPO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.modeling.api.dto.codestandard.CodeDetailDTO;
import io.yak.ops.modeling.api.dto.codestandard.CodeStandardDTO;
import io.yak.ops.modeling.api.vo.codestandard.CodeDetailVO;
import io.yak.ops.modeling.api.vo.codestandard.CodeStandardVO;
import io.yak.ops.modeling.application.service.CodeStandardService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standard-code and nested code-detail business service. */
@Service
public class CodeStandardServiceImpl
        extends AbstractModelingAssetService<CodeStandardPO, CodeStandardDTO, CodeStandardVO>
        implements CodeStandardService {

    @Override
    protected String resourceKey() {
        return "code-standard";
    }

    @Override
    protected Class<CodeStandardPO> entityClass() {
        return CodeStandardPO.class;
    }

    @Override
    protected Class<CodeStandardDTO> dtoClass() {
        return CodeStandardDTO.class;
    }

    @Override
    protected Class<CodeStandardVO> viewClass() {
        return CodeStandardVO.class;
    }

    @Override
    protected QueryWrapper<CodeStandardPO> buildQueryWrapper(CodeStandardDTO query) {
        QueryWrapper<CodeStandardPO> wrapper = new QueryWrapper<CodeStandardPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getParentId(), "parent_id");
        return wrapper;
    }

    @Override
    protected void validate(CodeStandardDTO dto) {
        requireFields(dto, "cnName", "enShortName", "enName");
    }

    @Override
    protected void beforeCreate(CodeStandardDTO dto) {
        validateDirectoryModule(dto.getParentId(), "CODE");
    }

    @Override
    protected void beforeUpdate(CodeStandardDTO dto, CodeStandardPO existing) {
        validateDirectoryModule(dto.getParentId(), "CODE");
    }

    @Override
    protected void beforeDelete(CodeStandardPO existing) {
        String id = existing.getId();
        if (modelingAssetDao.queryCount("field-standard",
                new QueryWrapper<FieldStandardPO>().eq("reference", id)) > 0
                || modelingAssetDao.queryCount("modifier",
                new QueryWrapper<ModifierPO>().eq("code_id", id)) > 0) {
            throw new IllegalArgumentException("该标准代码已被字段标准或指标修饰词引用，不能删除");
        }
    }

    @Override
    protected void afterCreate(CodeStandardPO created, CodeStandardDTO dto) {
        saveDetails(created.getId(), dto.getDetailList(), "sys");
    }

    @Override
    protected void afterUpdate(CodeStandardPO updated, CodeStandardDTO dto) {
        if (dto.getDetailList() != null) {
            modelingAssetDao.deleteByWrapper(
                    "code-detail", new QueryWrapper<CodeDetailPO>().eq("code_id", updated.getId()));
            saveDetails(updated.getId(), dto.getDetailList(), "sys");
        }
    }

    @Override
    protected void afterDelete(CodeStandardPO deleted) {
        modelingAssetDao.deleteByWrapper(
                "code-detail", new QueryWrapper<CodeDetailPO>().eq("code_id", deleted.getId()));
    }

    @Override
    protected CodeStandardVO toView(CodeStandardPO entity) {
        CodeStandardVO view = super.toView(entity);
        view.setDetailList(listDetails(entity.getId()));
        return view;
    }

    @Override
    public List<CodeDetailVO> listDetails(String codeId) {
        requireEntity(codeId);
        List<CodeDetailPO> details = modelingAssetDao.queryList(
                "code-detail",
                new QueryWrapper<CodeDetailPO>().eq("code_id", codeId).orderByAsc("create_time"));
        List<CodeDetailVO> result = new ArrayList<CodeDetailVO>(details.size());
        for (CodeDetailPO detail : details) {
            CodeDetailVO view = new CodeDetailVO();
            BeanUtils.copyProperties(detail, view);
            result.add(view);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDetail(String codeId, CodeDetailDTO dto, String operator) {
        requireEntity(codeId);
        validateDetail(dto);
        CodeDetailPO detail = new CodeDetailPO();
        BeanUtils.copyProperties(dto, detail);
        detail.setId(null);
        detail.setCodeId(codeId);
        detail.setProjectCode(null);
        detail.initInsert();
        detail.setCreateBy(resolveOperator(operator));
        detail.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.insert("code-detail", detail);
        return detail.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeDetailVO updateDetail(String codeId, String detailId, CodeDetailDTO dto, String operator) {
        requireEntity(codeId);
        validateDetail(dto);
        CodeDetailPO existing = requireDetail(codeId, detailId);
        CodeDetailPO detail = new CodeDetailPO();
        BeanUtils.copyProperties(dto, detail);
        detail.setId(detailId);
        detail.setCodeId(codeId);
        detail.setProjectCode(null);
        detail.initUpdate();
        detail.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.updateById("code-detail", detail);
        CodeDetailVO view = new CodeDetailVO();
        BeanUtils.copyProperties(modelingAssetDao.queryById("code-detail", existing.getId()), view);
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteDetail(String codeId, String detailId) {
        requireEntity(codeId);
        CodeDetailPO existing = requireDetail(codeId, detailId);
        if (modelingAssetDao.queryCount("modifier",
                new QueryWrapper<ModifierPO>().eq("code_detail_id", detailId)) > 0) {
            throw new IllegalArgumentException("该代码明细已被指标修饰词引用，不能删除");
        }
        modelingAssetDao.deleteById("code-detail", existing.getId());
        return detailId;
    }

    private void saveDetails(String codeId, List<CodeDetailDTO> details, String operator) {
        if (details == null) {
            return;
        }
        for (CodeDetailDTO dto : details) {
            if (dto == null) {
                continue;
            }
            validateDetail(dto);
            CodeDetailPO detail = new CodeDetailPO();
            BeanUtils.copyProperties(dto, detail);
            detail.setId(null);
            detail.setCodeId(codeId);
            detail.setProjectCode(null);
            detail.initInsert();
            detail.setCreateBy(resolveOperator(operator));
            detail.setUpdateBy(resolveOperator(operator));
            modelingAssetDao.insert("code-detail", detail);
        }
    }

    private void validateDetail(CodeDetailDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("标准代码明细不能为空");
        }
        requireFields(dto, "cnName", "enShortName", "enName");
    }

    private CodeDetailPO requireDetail(String codeId, String detailId) {
        CodeDetailPO detail = modelingAssetDao.queryById("code-detail", detailId);
        if (detail == null || !codeId.equals(detail.getCodeId())) {
            throw new IllegalArgumentException("标准代码明细不存在或不属于当前标准代码");
        }
        return detail;
    }
}
