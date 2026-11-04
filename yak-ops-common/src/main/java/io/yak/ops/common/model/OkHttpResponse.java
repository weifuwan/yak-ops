package io.yak.ops.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OkHttpResponse {

    private int statusCode;

    private String body;
}
