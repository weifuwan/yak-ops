package io.yak.ops.plugin.task.grpc.protobufjs.types;

import io.yak.ops.plugin.task.grpc.protobufjs.mapping.MapField;

import com.google.protobuf.DescriptorProtos;

public class MapFieldType {

    private MapFieldType() {
    }
    public static DescriptorProtos.FieldDescriptorProto.Builder parseMapField(DescriptorProtos.DescriptorProto.Builder parentMessage,
                                                                              String selfName, MapField mapField) {
        String mapEntryTypeName = "MapEntry_" + selfName;
        DescriptorProtos.FieldDescriptorProto.Builder mapFieldDescriptorProtoBuilder =
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName(selfName)
                        .setNumber(mapField.getId())
                        .setTypeName(mapEntryTypeName)
                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
        DescriptorProtos.DescriptorProto.Builder mapEntryDescriptorProtoBuilder =
                DescriptorProtos.DescriptorProto.newBuilder()
                        .setName(mapEntryTypeName)
                        .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true).build());
        DescriptorProtos.FieldDescriptorProto.Builder keyDescriptorProtoBuilder =
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("key")
                        .setNumber(1);
        DescriptorProtos.FieldDescriptorProto.Builder valueDescriptorProtoBuilder =
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("value")
                        .setNumber(2);
        try {
            keyDescriptorProtoBuilder
                    .setType(FieldType.parseFieldType(mapField.getKeyType()));
        } catch (IllegalArgumentException e) {
            keyDescriptorProtoBuilder.setTypeName(mapField.getKeyType());
        }
        try {
            valueDescriptorProtoBuilder
                    .setType(FieldType.parseFieldType(mapField.getType()));
        } catch (IllegalArgumentException e) {
            valueDescriptorProtoBuilder.setTypeName(mapField.getType());
        }
        mapEntryDescriptorProtoBuilder.addField(keyDescriptorProtoBuilder);
        mapEntryDescriptorProtoBuilder.addField(valueDescriptorProtoBuilder);
        parentMessage.addNestedType(mapEntryDescriptorProtoBuilder);

        return mapFieldDescriptorProtoBuilder;
    }
}
