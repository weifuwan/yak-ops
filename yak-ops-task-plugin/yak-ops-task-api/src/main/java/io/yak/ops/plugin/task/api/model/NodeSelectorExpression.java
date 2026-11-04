package io.yak.ops.plugin.task.api.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeSelectorExpression implements Serializable {

    /**
     * selector key
     */
    private String key;

    /**
     * selector operator
     */
    private String operator;

    /**
     * selector value
     */
    private String values;
}
