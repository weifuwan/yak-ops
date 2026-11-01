package io.yak.ops.dao.repository.task.impl;

import java.time.LocalDateTime;

/** One queued resource and the shared Instance order used across every resource group. */
final class TaskGroupClaim {
    private static final String TABLE = "DATA_SYNC_TARGET";
    private static final String SCOPE = "DATA_SYNC_TARGET_SCOPE";
    private final String instanceId;
    private final String type;
    private final String key;
    private final String datasource;
    private final int priority;
    private final boolean acquired;
    private final LocalDateTime created;

    TaskGroupClaim(String instanceId, String type, String key, int priority, boolean acquired, LocalDateTime created) {
        this.instanceId = instanceId;
        this.type = type;
        this.key = key;
        this.datasource = isTarget(type) ? datasource(type, key) : null;
        this.priority = priority;
        this.acquired = acquired;
        this.created = created;
    }

    String instanceId() {
        return instanceId;
    }

    boolean acquired() {
        return acquired;
    }

    boolean conflicts(TaskGroupClaim other) {
        if (!isTarget(type) || !isTarget(other.type)) {
            return type.equals(other.type) && key.equals(other.key);
        }
        return datasource.equals(other.datasource)
                && (SCOPE.equals(type) || SCOPE.equals(other.type) || key.equals(other.key));
    }

    boolean exclusiveTarget() {
        return isTarget(type);
    }

    Order order() {
        return new Order(instanceId, priority, created);
    }

    /** All claims of an Instance share its highest priority and earliest queue time. */
    static final class Order {
        private final String instanceId;
        private final int priority;
        private final LocalDateTime created;

        private Order(String instanceId, int priority, LocalDateTime created) {
            this.instanceId = instanceId;
            this.priority = priority;
            this.created = created;
        }

        Order merge(Order other) {
            if (!instanceId.equals(other.instanceId)) {
                throw new IllegalArgumentException("Cannot combine queue order for different Instances");
            }
            return new Order(
                    instanceId,
                    Math.max(priority, other.priority),
                    created.isBefore(other.created) ? created : other.created);
        }

        boolean precedes(Order other) {
            if (priority != other.priority) return priority > other.priority;
            int time = created.compareTo(other.created);
            return time < 0 || (time == 0 && instanceId.compareTo(other.instanceId) < 0);
        }
    }

    static boolean isTarget(String type) {
        return TABLE.equals(type) || SCOPE.equals(type);
    }

    static void validate(String type, String key) {
        if (isTarget(type)) {
            datasource(type, key);
        }
    }

    private static String datasource(String type, String key) {
        int separator = key == null ? -1 : key.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("Invalid data synchronization target resource");
        }
        int length;
        try {
            length = Integer.parseInt(key.substring(0, separator));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid data synchronization target resource", error);
        }
        if (length < 1 || length > key.length() - separator - 1) {
            throw new IllegalArgumentException("Invalid data synchronization target datasource");
        }
        int end = separator + 1 + length;
        if ((SCOPE.equals(type) && end != key.length()) || (TABLE.equals(type) && end == key.length())) {
            throw new IllegalArgumentException("Invalid data synchronization target scope");
        }
        return key.substring(0, end);
    }
}
