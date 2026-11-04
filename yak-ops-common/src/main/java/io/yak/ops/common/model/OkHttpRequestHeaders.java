package io.yak.ops.common.model;

import java.util.Map;

import lombok.Data;

@Data
public class OkHttpRequestHeaders {

    private Map<String, String> headers;

    private OkHttpRequestHeaderContentType okHttpRequestHeaderContentType;
}
