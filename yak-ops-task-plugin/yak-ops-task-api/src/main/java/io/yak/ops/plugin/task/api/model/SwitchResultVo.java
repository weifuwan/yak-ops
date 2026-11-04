package io.yak.ops.plugin.task.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SwitchResultVo {

    private String condition;
    private Long nextNode;
}
