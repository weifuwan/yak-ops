package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;

import java.util.List;
import java.util.Map;

/** Application contract for project-scoped standard and indicator assets. */
public interface ModelingAssetService {

    IPage<Map<String, Object>> page(String resource, Map<String, Object> params);

    List<Map<String, Object>> all(String resource, Map<String, Object> params);

    Map<String, Object> get(String resource, String id);

    String create(String resource, Map<String, Object> payload);

    Map<String, Object> update(String resource, Map<String, Object> payload);

    String delete(String resource, String id);

    Map<String, Object> submit(String resource, String id);

    List<Map<String, Object>> versions(String resource, String id);

    Map<String, Object> rollback(String resource, String id, Integer versionNumber);

    List<ModelingAssetTreeVO> directoryTree(String module);
}
