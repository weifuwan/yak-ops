package io.yak.ops.plugin.task.grpc.protobufjs.types;

import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Method;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Service;

import com.google.protobuf.DescriptorProtos;

public class ServiceType {

    private ServiceType() {
    }

    public static DescriptorProtos.ServiceDescriptorProto.Builder parseService(String selfName, Service service) {
        DescriptorProtos.ServiceDescriptorProto.Builder serviceDescriptorProtoBuilder =
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                        .setName(selfName);
        if (service.getMethods() != null)
            service.getMethods().forEach((name, pbObject) -> {
                if (pbObject instanceof Method) {
                    serviceDescriptorProtoBuilder.addMethod(MethodType.parseMethod(name, (Method) pbObject));
                }
            });
        return serviceDescriptorProtoBuilder;
    }
}
