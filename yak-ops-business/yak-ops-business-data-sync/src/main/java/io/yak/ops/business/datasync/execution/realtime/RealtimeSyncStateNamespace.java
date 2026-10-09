package io.yak.ops.business.datasync.execution.realtime;

import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 统一拥有 REALTIME CDC 持久化状态目录和稳定 Debezium engine identity。
 *
 * <p>状态按 Workspace / Task / definitionVersion 隔离；同一任务同一版本的后续 Instance 复用同一目录，
 * 从而让 Debezium FileOffsetBackingStore / FileSchemaHistory 可以继续消费已完成 Checkpoint 之后的 binlog。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class RealtimeSyncStateNamespace {

    private static final String HOME_PROPERTY = "yak.ops.home";
    private static final String STATE_ROOT_DIRECTORY = "data/data-sync/realtime";

    private final Path rootDirectory;

    public RealtimeSyncStateNamespace() {
        this(resolveRootDirectory());
    }

    RealtimeSyncStateNamespace(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "root directory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public Path stateDirectory(String workspaceId, String taskId, Integer taskVersion) {
        String workspace = requireSegment(workspaceId, "workspaceId");
        String task = requireSegment(taskId, "taskId");
        int version = requireVersion(taskVersion);

        Path directory = rootDirectory
                .resolve(workspace)
                .resolve(task)
                .resolve("v" + version)
                .normalize();
        if (!directory.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("realtime state directory escaped configured root");
        }
        return directory;
    }

    public String engineName(String workspaceId, String taskId, Integer taskVersion) {
        String workspace = requireSegment(workspaceId, "workspaceId");
        String task = requireSegment(taskId, "taskId");
        int version = requireVersion(taskVersion);
        return "yak-realtime-" + workspace + "-" + task + "-v" + version;
    }

    public String stateKey(String workspaceId, String taskId, Integer taskVersion) {
        return requireSegment(workspaceId, "workspaceId")
                + "/"
                + requireSegment(taskId, "taskId")
                + "/v"
                + requireVersion(taskVersion);
    }

    Path rootDirectory() {
        return rootDirectory;
    }

    private String requireSegment(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String segment = value.trim();
        if (segment.contains("/") || segment.contains("\\") || ".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException(field + " contains invalid path characters");
        }
        return segment;
    }

    private int requireVersion(Integer version) {
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("taskVersion must be greater than 0");
        }
        return version;
    }

    private static Path resolveRootDirectory() {
        String home = System.getProperty(HOME_PROPERTY);
        Path base = home == null || home.isBlank() ? Path.of(".") : Path.of(home);
        return base.resolve(STATE_ROOT_DIRECTORY);
    }
}
