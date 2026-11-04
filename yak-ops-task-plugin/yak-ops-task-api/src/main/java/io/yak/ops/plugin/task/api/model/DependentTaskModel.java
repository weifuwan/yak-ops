package io.yak.ops.plugin.task.api.model;

import io.yak.ops.plugin.task.api.enums.DependentRelation;

import java.util.List;

import lombok.Data;

@Data
public class DependentTaskModel {

    private List<DependentItem> dependItemList;
    private DependentRelation relation;

}
