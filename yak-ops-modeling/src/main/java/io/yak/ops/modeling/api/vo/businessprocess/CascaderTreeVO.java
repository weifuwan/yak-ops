package io.yak.ops.modeling.api.vo.businessprocess;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CascaderTreeVO {

    private String key;

    private String parentId;

    private String label;

    private String value;

    private List<CascaderTreeVO> children;
}
