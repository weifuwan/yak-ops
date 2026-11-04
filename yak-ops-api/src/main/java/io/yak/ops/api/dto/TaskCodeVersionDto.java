package io.yak.ops.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskCodeVersionDto {

    private long code;
    private int version;
}
