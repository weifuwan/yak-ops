package io.yak.ops.modeling.api.vo.tree;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeTypeVO {

    private String key;

    private String title;

    private String parentId;

    private String type;

    private List<TreeTypeVO> children;
}