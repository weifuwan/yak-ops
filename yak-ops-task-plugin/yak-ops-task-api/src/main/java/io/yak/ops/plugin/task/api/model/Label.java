package io.yak.ops.plugin.task.api.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Label implements Serializable {

    /**
     * label name
     */
    private String label;

    /**
     * label value
     */
    private String value;
}
