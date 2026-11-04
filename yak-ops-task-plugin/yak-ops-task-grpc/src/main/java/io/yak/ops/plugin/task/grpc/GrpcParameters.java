package io.yak.ops.plugin.task.grpc;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.grpc.protobufjs.GrpcDynamicService;
import io.yak.ops.plugin.task.grpc.protobufjs.JSONDescriptorHelper;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.Descriptors;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GrpcParameters extends AbstractParameters {

    private String url;

    private GrpcCredentialType channelCredentialType = GrpcCredentialType.INSECURE;

    private String grpcServiceDefinition;

    private String grpcServiceDefinitionJSON;

    private String methodName;

    private String message;

    private GrpcCheckCondition grpcCheckCondition = GrpcCheckCondition.STATUS_CODE_DEFAULT;

    private String condition;

    @JsonProperty("grpcConnectTimeoutMs")
    private long connectTimeoutMs = 0L;

    @Override
    public boolean checkParameters() {
        // validate JSON formatted proto definition
        try {
            Descriptors.FileDescriptor fileDesc =
                    JSONDescriptorHelper.fileDescFromJSON(grpcServiceDefinitionJSON);
            GrpcDynamicService.mergeJSON(fileDesc, methodName, message);
        } catch (Descriptors.DescriptorValidationException | RuntimeException e) {
            return false;
        }
        return !(StringUtils.isEmpty(url) || connectTimeoutMs <= 0);
    }
}
