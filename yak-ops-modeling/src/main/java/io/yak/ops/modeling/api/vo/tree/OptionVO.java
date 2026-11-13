package io.yak.ops.modeling.api.vo.tree;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionVO {

    private Object value;

    private Object label;
}