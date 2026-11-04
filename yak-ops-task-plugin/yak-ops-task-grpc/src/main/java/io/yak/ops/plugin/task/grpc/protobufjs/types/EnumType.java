package io.yak.ops.plugin.task.grpc.protobufjs.types;

import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Enum;

import com.google.protobuf.DescriptorProtos;

public class EnumType {

    private EnumType() {
    }

    public static DescriptorProtos.EnumDescriptorProto.Builder parseEnum(String selfName, Enum enumObj) {
        DescriptorProtos.EnumDescriptorProto.Builder enumDescriptorProtoBuilder =
                DescriptorProtos.EnumDescriptorProto.newBuilder()
                        .setName(selfName);
        enumObj.getValues().forEach((name, id) -> {
            DescriptorProtos.EnumValueDescriptorProto.Builder enumValueDescriptorProtoBuilder =
                    DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                            .setName(name)
                            .setNumber(id);
            enumDescriptorProtoBuilder.addValue(enumValueDescriptorProtoBuilder);
        });
        return enumDescriptorProtoBuilder;
    }
}
