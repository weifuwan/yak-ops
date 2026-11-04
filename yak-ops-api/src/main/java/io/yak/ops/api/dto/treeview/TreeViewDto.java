package io.yak.ops.api.dto.treeview;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class TreeViewDto {

    private String name;

    private String type;

    private long code;

    private List<Instance> instances = new ArrayList<>();

    private List<TreeViewDto> children = new ArrayList<>();

}
