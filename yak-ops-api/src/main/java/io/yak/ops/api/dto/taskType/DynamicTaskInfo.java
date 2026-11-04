package io.yak.ops.api.dto.taskType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class DynamicTaskInfo {

    private String name;
    private String hover;
    private String icon;
    private String json;

}
