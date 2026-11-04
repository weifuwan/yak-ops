package io.yak.ops.plugin.task.grpc.protobufjs.types;

import com.google.protobuf.DescriptorProtos;

public class OneofType {

    private OneofType() {
    }

    public static DescriptorProtos.OneofDescriptorProto.Builder parseOneof(DescriptorProtos.DescriptorProto.Builder descriptorProtoBuilder,
                                                                           String selfName) {
        DescriptorProtos.OneofDescriptorProto.Builder oneofDescriptorProtoBuilder =
                DescriptorProtos.OneofDescriptorProto.newBuilder()
                        .setName(selfName);
        descriptorProtoBuilder.addOneofDecl(oneofDescriptorProtoBuilder);
        return oneofDescriptorProtoBuilder;
    }
}
