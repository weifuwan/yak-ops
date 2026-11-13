package io.yak.ops.modeling.api.vo.tree;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Tree node used by data-standard directories and their records. */
@Data
public class ModelingAssetTreeVO {
    private String id;
    private String key;
    private String value;
    private String title;
    private String parentId;
    private String type;
    private List<ModelingAssetTreeVO> children = new ArrayList<>();
}
