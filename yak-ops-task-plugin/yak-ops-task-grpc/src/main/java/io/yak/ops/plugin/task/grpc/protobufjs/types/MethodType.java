package io.yak.ops.plugin.task.grpc.protobufjs.types;

import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Method;

import com.google.protobuf.DescriptorProtos;

public class MethodType {

    private MethodType() {
    }

    public static DescriptorProtos.MethodDescriptorProto.Builder parseMethod(String selfName, Method method) {
        return DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setName(selfName)
                .setInputType(method.getRequestType())
                .setOutputType(method.getResponseType());
    }
}
