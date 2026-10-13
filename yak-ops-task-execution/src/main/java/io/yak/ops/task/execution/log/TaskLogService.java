package io.yak.ops.task.execution.log;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.bean.vo.task.TaskLogPageVO;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reads Workspace-authorized execution logs without accepting client-selected file paths. */
@Service
public class TaskLogService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskLogService.class);

    private static final int MAX_LINES = 500;
    private static final int MAX_PAGE_BYTES = 65536;
    private static final int MAX_LINE_LENGTH = 8192;
    private static final Pattern ARCHIVE = Pattern.compile("task\\.(\\d{4}-\\d{2}-\\d{2})\\.(\\d{1,5})\\.log\\.gz");

    @Resource
    private InstanceService instances;

    @Resource
    private InstanceRepository repository;

    @Value("${yak.task.logging.directory:logs/tasks}")
    private String directory;

    @Value("${yak.task.logging.max-history-days:7}")
    private int retentionDays;

    /**
     * Reads a bounded page including retained archives.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance ID
     * @param cursor previously returned opaque line position
     * @param limit maximum number of lines, from 1 to 500
     * @return a bounded page and its next position
     * @throws IOException if a retained log file cannot be read
     */
    public TaskLogPageVO read(String workspaceId, String instanceId, String cursor, int limit) throws IOException {
        if (limit < 1 || limit > MAX_LINES) {
            throw new IllegalArgumentException("Task log page size must be between 1 and 500");
        }
        long skipped = decodeCursor(cursor);
        Path taskDirectory = directory(workspaceId, instanceId);
        if (taskDirectory == null) {
            return new TaskLogPageVO("", encodeCursor(skipped), false);
        }
        StringBuilder contents = new StringBuilder();
        long position = 0;
        int lines = 0;
        int bytes = 0;
        boolean more = false;
        reading:
        for (Path segment : segments(taskDirectory)) {
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(open(segment), StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = reader.readLine()) != null) {
                    if (position < skipped) {
                        position++;
                        continue;
                    }
                    String line = raw;
                    if (raw.length() > MAX_LINE_LENGTH) {
                        line = raw.substring(0, MAX_LINE_LENGTH) + " [truncated]";
                    }
                    int length = line.getBytes(StandardCharsets.UTF_8).length + 1;
                    if (lines >= limit || (lines > 0 && bytes + length > MAX_PAGE_BYTES)) {
                        more = true;
                        break reading;
                    }
                    contents.append(line).append('\n');
                    bytes += length;
                    lines++;
                    position++;
                }
            }
        }
        return new TaskLogPageVO(contents.toString(), encodeCursor(Math.max(position, skipped)), more);
    }

    /**
     * Streams all retained segments as UTF-8 text without loading the full log into memory.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId Task Instance ID
     * @param target response stream
     * @throws IOException if a retained log file cannot be read
     */
    public void download(String workspaceId, String instanceId, OutputStream target) throws IOException {
        Path taskDirectory = directory(workspaceId, instanceId);
        if (taskDirectory == null) {
            return;
        }
        for (Path segment : segments(taskDirectory)) {
            try (InputStream in = open(segment)) {
                in.transferTo(target);
            }
        }
    }

    /**
     * Removes old local log files only after their owning Instance reaches a terminal state.
     *
     * <p>Active CDC Instances retain their current log and Logback enforces archive rolling limits.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupExpired() {
        if (retentionDays < 1) {
            return;
        }
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        try (var directories = Files.list(root)) {
            for (Path task : directories.toList()) {
                String id = task.getFileName().toString();
                if (!id.matches("[A-Za-z0-9_-]{1,128}")
                        || !Files.isDirectory(task, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(task)) {
                    continue;
                }
                Optional<InstanceEntity> instance = repository.findByIdForLogCleanup(id);
                if (instance.isEmpty()
                        || instance.orElseThrow().getStatus() == null
                        || !instance.orElseThrow().getStatus().isTerminal()
                        || !("tasks/" + id + "/task.log")
                                .equals(instance.orElseThrow().getLogPath())) {
                    continue;
                }
                List<Path> files = segments(task);
                if (files.isEmpty()) {
                    continue;
                }
                boolean expired = true;
                for (Path file : files) {
                    if (!Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)
                            .toInstant()
                            .isBefore(cutoff)) {
                        expired = false;
                        break;
                    }
                }
                if (expired) {
                    for (Path file : files) {
                        Files.delete(file);
                    }
                    try {
                        Files.delete(task);
                    } catch (DirectoryNotEmptyException ignored) {
                        // Only Task log segments are managed; unrelated files stay untouched.
                    }
                }
            }
        } catch (IOException failure) {
            LOG.warn(
                    "Task log retention cleanup failed, exceptionType={}",
                    failure.getClass().getSimpleName());
        }
    }

    private Path directory(String workspaceId, String instanceId) {
        InstanceEntity instance = instances.instance(workspaceId, instanceId);
        if (instance.getLogPath() == null) {
            return null;
        }
        if (!instanceId.matches("[A-Za-z0-9_-]{1,128}")
                || !instance.getLogPath().equals("tasks/" + instanceId + "/task.log")) {
            throw new IllegalStateException("Invalid persisted Task Instance log path");
        }
        Path root = Path.of(directory).toAbsolutePath().normalize();
        Path target = root.resolve(instanceId).normalize();
        if (!target.startsWith(root) || Files.isSymbolicLink(root) || Files.isSymbolicLink(target)) {
            throw new IllegalStateException("Unsafe Task Instance log directory");
        }
        return target;
    }

    private static List<Path> segments(Path taskDirectory) throws IOException {
        if (!Files.isDirectory(taskDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var files = Files.list(taskDirectory)) {
            List<Path> ordered = new ArrayList<>(files.filter(
                            file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.equals("task.log") || ARCHIVE.matcher(name).matches();
                    })
                    .toList());
            ordered.sort(Comparator.comparing(TaskLogService::sortKey));
            return ordered;
        }
    }

    private static String sortKey(Path path) {
        String name = path.getFileName().toString();
        if (name.equals("task.log")) {
            return "9999-99-99-999999";
        }
        Matcher match = ARCHIVE.matcher(name);
        if (!match.matches()) {
            throw new IllegalStateException("Invalid Task log segment");
        }
        return match.group(1) + "-" + String.format(java.util.Locale.ROOT, "%06d", Integer.parseInt(match.group(2)));
    }

    private static InputStream open(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Task log segment is not a regular file");
        }
        InputStream input = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            try {
                return new GZIPInputStream(input);
            } catch (IOException error) {
                input.close();
                throw error;
            }
        }
        return input;
    }

    private static long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        if (cursor.length() > 32 || !cursor.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid Task log cursor");
        }
        try {
            String offset = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!offset.matches("(0|[1-9][0-9]{0,11})")) {
                throw new IllegalArgumentException("Invalid Task log cursor");
            }
            return Long.parseLong(offset);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid Task log cursor", error);
        }
    }

    private static String encodeCursor(long offset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Long.toString(offset).getBytes(StandardCharsets.US_ASCII));
    }
}
