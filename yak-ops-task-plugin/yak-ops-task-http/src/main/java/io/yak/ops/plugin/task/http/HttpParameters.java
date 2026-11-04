package io.yak.ops.plugin.task.http;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * http parameter
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class HttpParameters extends AbstractParameters {

    private String url;

    @JsonProperty("httpMethod")
    private HttpRequestMethod httpRequestMethod;

    @JsonProperty("httpParams")
    private List<HttpProperty> httpRequestParams;

    @JsonProperty("httpBody")
    private String httpRequestBody;

    private HttpCheckCondition httpCheckCondition = HttpCheckCondition.STATUS_CODE_DEFAULT;

    private String condition;

    /**
     * Connect Timeout
     * Unit: ms
     */
    private int connectTimeout;

    @Override
    public boolean checkParameters() {
        return StringUtils.isNotEmpty(url) && httpRequestMethod != null
                && connectTimeout > 0;
    }

}
