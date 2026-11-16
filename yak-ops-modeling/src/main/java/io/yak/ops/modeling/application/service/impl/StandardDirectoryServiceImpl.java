package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.dao.entity.CodeStandardPO;
import io.yak.ops.dao.entity.FieldStandardPO;
import io.yak.ops.dao.entity.StandardDirectoryPO;
import io.yak.ops.dao.entity.UnitStandardPO;
import io.yak.ops.modeling.api.dto.standarddirectory.StandardDirectoryDTO;
import io.yak.ops.modeling.api.vo.standarddirectory.StandardDirectoryVO;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.application.service.StandardDirectoryService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Standard directory management and shared standard-tree assembly. */
@Service
public class StandardDirectoryServiceImpl
        extends AbstractModelingAssetService<StandardDirectoryPO, StandardDirectoryDTO, StandardDirectoryVO>
        implements StandardDirectoryService {

    @Override
    protected String resourceKey() {
        return "standard-directory";
    }

    @Override
    protected Class<StandardDirectoryPO> entityClass() {
        return StandardDirectoryPO.class;
    }

    @Override
    protected Class<StandardDirectoryDTO> dtoClass() {
        return StandardDirectoryDTO.class;
    }

    @Override
    protected Class<StandardDirectoryVO> viewClass() {
        return StandardDirectoryVO.class;
    }

    @Override
    protected QueryWrapper<StandardDirectoryPO> buildQueryWrapper(StandardDirectoryDTO query) {
        QueryWrapper<StandardDirectoryPO> wrapper = new QueryWrapper<StandardDirectoryPO>();
        addSearch(wrapper, query, "dir_name");
        eqIfNotBlank(wrapper, query.getParentId(), "parent_id");
        eqIfNotBlank(wrapper, query.getDirModule(), "dir_module");
        eqIfNotBlank(wrapper, query.getDirType(), "dir_type");
        return wrapper;
    }

    @Override
    protected void validate(StandardDirectoryDTO dto) {
        requireFields(dto, "dirName", "dirModule", "dirType");
        validateDirectoryType(dto.getDirModule(), dto.getDirType());
        validateDirectoryModule(dto.getParentId(), dto.getDirModule());
    }

    @Override
    protected void beforeCreate(StandardDirectoryDTO dto) {
        validateDirectoryParent(null, dto.getParentId(), dto.getDirModule());
        validateDirectoryUnique(dto, null);
    }

    @Override
    protected void beforeUpdate(StandardDirectoryDTO dto, StandardDirectoryPO existing) {
        validateDirectoryParent(existing.getId(), dto.getParentId(), dto.getDirModule());
        validateDirectoryUnique(dto, existing.getId());
    }

    @Override
    protected void beforeDelete(StandardDirectoryPO existing) {
        String id = existing.getId();
        if (modelingAssetDao.queryCount("standard-directory",
                new QueryWrapper<StandardDirectoryPO>().eq("parent_id", id)) > 0
                || modelingAssetDao.queryCount("field-standard",
                new QueryWrapper<FieldStandardPO>().eq("parent_id", id)) > 0
                || modelingAssetDao.queryCount("code-standard",
                new QueryWrapper<CodeStandardPO>().eq("parent_id", id)) > 0
                || modelingAssetDao.queryCount("unit-standard",
                new QueryWrapper<UnitStandardPO>().eq("parent_id", id)) > 0) {
            throw new IllegalArgumentException("目录下仍有子目录或标准记录，请先清空后再删除");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<StandardDirectoryVO> page(StandardDirectoryDTO query) {
        if (query != null && "UNIT".equalsIgnoreCase(query.getDirModule())) {
            ensureDefaultUnitDirectories();
        }
        return super.page(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<StandardDirectoryVO> list(StandardDirectoryDTO query) {
        if (query != null && "UNIT".equalsIgnoreCase(query.getDirModule())) {
            ensureDefaultUnitDirectories();
        }
        return super.list(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ModelingAssetTreeVO> tree(String module) {
        String type = text(module).toUpperCase(Locale.ROOT);
        if (!Arrays.asList("FIELD", "CODE", "UNIT").contains(type)) {
            throw new IllegalArgumentException("目录模块必须是 FIELD、CODE 或 UNIT");
        }
        if ("UNIT".equals(type)) {
            ensureDefaultUnitDirectories();
        }

        List<ModelingAssetTreeVO> nodes = new ArrayList<ModelingAssetTreeVO>();
        List<StandardDirectoryPO> directories = modelingAssetDao.queryList(
                "standard-directory",
                new QueryWrapper<StandardDirectoryPO>().eq("dir_module", type).orderByAsc("dir_name"));
        for (StandardDirectoryPO item : directories) {
            nodes.add(node(item.getId(), item.getDirName(), item.getParentId(), item.getDirType()));
        }

        if ("FIELD".equals(type)) {
            for (FieldStandardPO item : modelingAssetDao.queryList(
                    "field-standard", new QueryWrapper<FieldStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "FIELD_STANDARD"));
            }
        } else if ("CODE".equals(type)) {
            for (CodeStandardPO item : modelingAssetDao.queryList(
                    "code-standard", new QueryWrapper<CodeStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "CODE_STANDARD"));
            }
        } else {
            for (UnitStandardPO item : modelingAssetDao.queryList(
                    "unit-standard", new QueryWrapper<UnitStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "UNIT_STANDARD"));
            }
        }

        Map<String, ModelingAssetTreeVO> byId = new LinkedHashMap<String, ModelingAssetTreeVO>();
        for (ModelingAssetTreeVO item : nodes) {
            byId.put(item.getId(), item);
        }
        List<ModelingAssetTreeVO> roots = new ArrayList<ModelingAssetTreeVO>();
        for (ModelingAssetTreeVO item : nodes) {
            ModelingAssetTreeVO parent = item.getParentId() == null ? null : byId.get(item.getParentId());
            if (parent == null) {
                roots.add(item);
            } else {
                parent.getChildren().add(item);
            }
        }
        return roots;
    }

    private void validateDirectoryType(String module, String dirType) {
        String normalizedModule = text(module).toUpperCase(Locale.ROOT);
        if (!Arrays.asList("FIELD", "CODE", "UNIT").contains(normalizedModule)) {
            throw new IllegalArgumentException("标准目录模块必须是 FIELD、CODE 或 UNIT");
        }
        Set<String> allowed = new HashSet<String>();
        allowed.add("FOLDER");
        if ("FIELD".equals(normalizedModule)) {
            allowed.add("STANDARD_SET");
            allowed.add("STANDARD");
        } else if ("CODE".equals(normalizedModule)) {
            allowed.add("CODE");
        } else {
            allowed.add("UNIT");
        }
        if (!allowed.contains(text(dirType).toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("目录类型与所属标准模块不匹配");
        }
    }

    private void validateDirectoryParent(String id, String parentId, String module) {
        validateDirectoryModule(parentId, module);
        if (!notBlank(parentId)) {
            return;
        }
        Set<String> visited = new HashSet<String>();
        String currentId = parentId;
        while (notBlank(currentId) && visited.add(currentId)) {
            if (id != null && id.equals(currentId)) {
                throw new IllegalArgumentException("目录不能移动到自身或自己的子目录下");
            }
            StandardDirectoryPO parent = modelingAssetDao.queryById("standard-directory", currentId);
            currentId = parent == null ? "" : text(parent.getParentId());
        }
    }

    private void validateDirectoryUnique(StandardDirectoryDTO dto, String excludeId) {
        QueryWrapper<StandardDirectoryPO> wrapper = new QueryWrapper<StandardDirectoryPO>()
                .eq("dir_name", dto.getDirName())
                .eq("dir_module", dto.getDirModule());
        if (notBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (modelingAssetDao.queryCount("standard-directory", wrapper) > 0) {
            throw new IllegalArgumentException("该标准模块下已存在同名目录");
        }
    }

    private void ensureDefaultUnitDirectories() {
        Long count = modelingAssetDao.queryCount("standard-directory",
                new QueryWrapper<StandardDirectoryPO>().eq("dir_module", "UNIT"));
        if (count != null && count.longValue() > 0) {
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

    private ModelingAssetTreeVO node(String id, String title, String parentId, String type) {
        ModelingAssetTreeVO node = new ModelingAssetTreeVO();
        node.setId(id);
        node.setKey(type + ":" + id);
        node.setValue(id);
        node.setTitle(title);
        node.setParentId(parentId);
        node.setType(type);
        node.setChildren(new ArrayList<ModelingAssetTreeVO>());
        return node;
    }
}
