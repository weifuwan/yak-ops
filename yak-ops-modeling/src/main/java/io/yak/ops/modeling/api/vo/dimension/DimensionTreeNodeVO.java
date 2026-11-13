package io.yak.ops.modeling.api.vo.dimension;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Tree node returned by dimension modeling, retaining the legacy tree payload shape. */
@Data
public class DimensionTreeNodeVO {
    private String key;
    private String title;
    private String parentId;
    private String type;
    private Integer total;
    private boolean leaf;
    private Date updateTime;
    private List<DimensionTreeNodeVO> children = new ArrayList<>();
}