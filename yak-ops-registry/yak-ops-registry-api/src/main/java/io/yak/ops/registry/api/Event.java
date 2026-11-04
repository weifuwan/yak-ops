package io.yak.ops.registry.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
@AllArgsConstructor
public class Event {

    // The path which is watched
    private final String watchedPath;
    // The full path where the event was generated
    private final String eventPath;
    // The value corresponding to the path
    private final String eventData;
    // The event type {ADD, REMOVE, UPDATE}
    private Type type;

    public enum Type {
        ADD,
        REMOVE,
        UPDATE
    }

}
