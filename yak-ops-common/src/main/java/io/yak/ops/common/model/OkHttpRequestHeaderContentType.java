package io.yak.ops.common.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OkHttpRequestHeaderContentType {

    APPLICATION_JSON("application/json"),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded");

    private final String value;

    public static OkHttpRequestHeaderContentType fromValue(String value) {
        for (OkHttpRequestHeaderContentType contentType : OkHttpRequestHeaderContentType.values()) {
            if (contentType.getValue().equalsIgnoreCase(value)) {
                return contentType;
            }
        }
        return null;
    }
}
