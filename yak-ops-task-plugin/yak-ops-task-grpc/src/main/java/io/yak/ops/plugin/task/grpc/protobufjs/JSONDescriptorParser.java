package io.yak.ops.plugin.task.grpc.protobufjs;

import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Enum;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Namespace;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Root;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Service;
import io.yak.ops.plugin.task.grpc.protobufjs.mapping.Type;
import io.yak.ops.plugin.task.grpc.protobufjs.types.EnumType;
import io.yak.ops.plugin.task.grpc.protobufjs.types.ServiceType;
import io.yak.ops.plugin.task.grpc.protobufjs.types.TypeType;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

public class JSONDescriptorParser {

    public Descriptors.FileDescriptor buildDescriptor(Root root) throws Descriptors.DescriptorValidationException {
        return parseRoot(root);
    }

    private Namespace findInnerNamespace(Namespace ns) {
        while (true) {
            if (ns.getNested() != null && ns.getNested().size() == 1
                    && ns.getNested().values().toArray()[0] instanceof Namespace && !(ns.getNested() instanceof Type)
                    && !(ns.getNested() instanceof Service)) {
                ns = (Namespace) ns.getNested().values().toArray()[0];
            } else {
                break;
            }
        }
        return ns;
    }

    public String readPackageName(Root root) {
        List<String> packageNameNS = new ArrayList<>();
        Namespace ns = root;
        while (true) {
            if (ns.getNested() != null && ns.getNested().size() == 1
                    && ns.getNested().values().toArray()[0] instanceof Namespace && !(ns.getNested() instanceof Type)
                    && !(ns.getNested() instanceof Service)) {
                packageNameNS.add((String) ns.getNested().keySet().toArray()[0]);
                ns = (Namespace) ns.getNested().values().toArray()[0];
            } else {
                break;
            }
        }
        return String.join(GrpcConstants.NAMESPACE_SEPERATOR, packageNameNS);
    }

    private Descriptors.FileDescriptor parseRoot(Root root) throws Descriptors.DescriptorValidationException {
        DescriptorProtos.FileDescriptorProto.Builder fileDescriptorProtoBuilder =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setPackage(readPackageName(root));
        Namespace innerNS = findInnerNamespace(root);
        if (innerNS.getNested() != null)
            innerNS.getNested().forEach((name, pbObject) -> {
                if (pbObject instanceof Namespace) {
                    Namespace ns = (Namespace) pbObject;
                    if (ns instanceof Type) {
                        fileDescriptorProtoBuilder.addMessageType(TypeType.parseType(name, (Type) ns));
                    } else if (ns instanceof Service) {
                        fileDescriptorProtoBuilder.addService(ServiceType.parseService(name, (Service) ns));
                    }
                } else if (pbObject instanceof Enum) {
                    fileDescriptorProtoBuilder.addEnumType(EnumType.parseEnum(name, (Enum) pbObject));
                }
            });
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProtoBuilder.build(),
                new Descriptors.FileDescriptor[0]);
    }

}
