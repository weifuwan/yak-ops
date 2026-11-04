package io.yak.ops.plugin.task.grpc.protobufjs.mapping;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class OneOf extends ReflectionObject {

    @JsonProperty("oneof")
    private ArrayList<String> oneofList;
}
