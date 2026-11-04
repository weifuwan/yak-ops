package io.yak.ops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class FavTaskDto implements Cloneable {

    // task type name
    private String taskType;
    private boolean isCollection;
    // task category the task type belongs to
    private String taskCategory;

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
