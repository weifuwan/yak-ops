package io.yak.ops.modeling.api.vo.tree;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeVO {

    private String key;

    private String value;

    private String title;

    private String parentId;

    private List<TreeVO> children;
}