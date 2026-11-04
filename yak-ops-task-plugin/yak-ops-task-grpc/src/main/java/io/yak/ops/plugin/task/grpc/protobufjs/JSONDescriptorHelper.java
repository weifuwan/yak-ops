package io.yak.ops.plugin.task.grpc.protobufjs;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Root;

import com.google.protobuf.Descriptors;

public class JSONDescriptorHelper {

    private JSONDescriptorHelper() {
    }

    public static Root protobufFromJSON(String json) {
        return JSONUtils.parseObject(json, Root.class);
    }

    public static Descriptors.FileDescriptor fileDescFromJSON(String json) throws Descriptors.DescriptorValidationException {
        JSONDescriptorParser parser = new JSONDescriptorParser();
        return parser.buildDescriptor(protobufFromJSON(json));
    }
}
