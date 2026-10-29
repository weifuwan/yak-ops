package io.yak.ops.e2e.support;

import static org.awaitility.Awaitility.await;

import io.yak.ops.testutils.DatabaseMetadata;
import io.yak.ops.testutils.TestNames;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Owns one packaged application, its metadata database, state and retained diagnostics. */
public final class BootProcess implements ExtensionContext.Store.CloseableResource {
    public static final String USER = "backendtest";
    public static final String PASSWORD = "backend-e2e-only-password";
    private static final Pattern PORT = Pattern.compile("Tomcat started on port ([0-9]+)");
    private final DatabaseMetadata server;
    private final String databaseName = TestNames.unique("platform");
    private final Path directory;
    private DatabaseMetadata metadata;
    private Process process;
    private int port;
    private int generation;
    private boolean created;

    public BootProcess(DatabaseMetadata server) throws IOException {
        this.server = server;
        directory = Path.of("target", "test-artifacts", "boot", databaseName).toAbsolutePath();
        Files.createDirectories(directory.resolve("state"));
    }

    public void start() throws Exception {
        server.execute("CREATE DATABASE " + databaseName);
        created = true;
        metadata = mysqlDatabase(server, databaseName);
        boot();
    }

    private void boot() throws Exception {
        Properties build = new Properties();
        try (var input = BootProcess.class.getResourceAsStream("/backend-build.properties")) {
            if (input == null) throw new IllegalStateException("Missing Maven-filtered backend-build.properties");
            build.load(input);
        }
        Path jar = Path.of(System.getProperty("yak.e2e.jar", build.getProperty("yak.e2e.jar")))
                .toAbsolutePath();
        if (!Files.isRegularFile(jar))
            throw new IllegalStateException("Build the Boot JAR with Maven before running Backend E2E: " + jar);
        Path driverRoot = Path.of(System.getProperty(
                        "yak.e2e.jdbc-driver-dir", build.getProperty("yak.e2e.jdbc-driver-dir")))
                .toAbsolutePath();
        if (!Files.isDirectory(driverRoot.resolve("mysql/8")))
            throw new IllegalStateException("Missing tracked JDBC driver directory: " + driverRoot);
        Files.writeString(
                directory.resolve("build.json"),
                ApiClient.json(
                        Map.of("jar", jar.toString(), "sha256", HexFormat.of().formatHex(hash(jar)))));
        Path output = directory.resolve("application-" + (++generation) + ".log");
        ProcessBuilder command = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dyak.ops.jdbc-driver-dir=" + driverRoot,
                "-jar",
                jar.toString(),
                "--server.port=0",
                // Keep the upstream Registry three-heartbeat validation while allowing
                // crash-recovery tests to exercise bounded lease expiration promptly.
                "--registry.session-timeout=PT9S",
                "--registry.heartbeat-refresh-interval=PT3S",
                "--yak.task.execution.state-directory=" + directory.resolve("state"));
        var env = command.environment();
        env.keySet().removeIf(key -> key.startsWith("YAK_") || key.startsWith("SPRING_"));
        env.put("YAK_DATABASE_URL", metadata.url());
        env.put("YAK_DATABASE_USERNAME", metadata.username());
        env.put("YAK_DATABASE_PASSWORD", metadata.password());
        env.put("YAK_SECURITY_BOOTSTRAP_ENABLED", "true");
        env.put("YAK_SECURITY_BOOTSTRAP_USERNAME", USER);
        env.put("YAK_SECURITY_BOOTSTRAP_PASSWORD", PASSWORD);
        env.put("YAK_SECURITY_BOOTSTRAP_REAL_NAME", "Backend Tests");
        env.put("YAK_OPS_DATASOURCE_MASTER_KEY", "backend-test-isolated-master-key");
        env.put("YAK_LOG_FILE", directory.resolve("app.log").toString());
        env.put(
                "YAK_LOG_FILE_PATTERN",
                directory.resolve("app.%d{yyyy-MM-dd}.%i.log.gz").toString());
        env.put("YAK_TASK_LOG_DIRECTORY", directory.resolve("tasks").toString());
        long started = System.nanoTime();
        process = command.redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        HttpClient health =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        try {
            await().alias("Packaged Boot readiness")
                    .pollInterval(Duration.ofMillis(200))
                    .atMost(Duration.ofSeconds(150))
                    .until(() -> {
                        if (!process.isAlive())
                            throw new IllegalStateException("Boot exited before readiness; see " + output);
                        String text = Files.readString(output);
                        var matcher = PORT.matcher(text);
                        if (!matcher.find() || !text.contains("Started YakOpsApplication")) return false;
                        port = Integer.parseInt(matcher.group(1));
                        try {
                            return health.send(
                                                    HttpRequest.newBuilder(URI.create(url() + "/v3/api-docs"))
                                                            .timeout(Duration.ofSeconds(2))
                                                            .GET()
                                                            .build(),
                                                    HttpResponse.BodyHandlers.discarding())
                                            .statusCode()
                                    == 200;
                        } catch (IOException notReady) {
                            return false;
                        }
                    });
        } catch (Exception failure) {
            diagnostics();
            stop(true);
            throw failure;
        }
        Files.writeString(
                directory.resolve("startup-" + generation + ".json"),
                ApiClient.json(Map.of("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))));
    }

    private static byte[] hash(Path jar) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(jar)) {
            byte[] buffer = new byte[65536];
            for (int size; (size = input.read(buffer)) != -1; ) digest.update(buffer, 0, size);
        }
        return digest.digest();
    }

    public String url() {
        return "http://127.0.0.1:" + port;
    }

    public DatabaseMetadata server() {
        return server;
    }

    public DatabaseMetadata metadata() {
        return metadata;
    }

    public Path directory() {
        return directory;
    }

    public ApiClient login() {
        ApiClient api = new ApiClient(url());
        api.call("POST", "/api/v1/account/login", Map.of("userName", USER, "pw", PASSWORD));
        return api;
    }

    public void restart(boolean force) throws Exception {
        stop(force);
        boot();
    }

    public void stop(boolean force) throws Exception {
        if (process == null || !process.isAlive()) return;
        if (force) process.destroyForcibly();
        else process.destroy();
        if (!process.waitFor(35, TimeUnit.SECONDS)) {
            diagnostics();
            process.destroyForcibly();
            if (!process.waitFor(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Boot process did not terminate");
        }
    }

    public void diagnostics() {
        if (process != null && process.isAlive()) {
            try {
                Process dump = new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "jcmd")
                                        .toString(),
                                Long.toString(process.pid()),
                                "Thread.print",
                                "-l")
                        .redirectErrorStream(true)
                        .redirectOutput(directory
                                .resolve("threads-" + generation + ".txt")
                                .toFile())
                        .start();
                if (!dump.waitFor(5, TimeUnit.SECONDS)) dump.destroyForcibly();
            } catch (Exception failure) {
                if (failure instanceof InterruptedException)
                    Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() throws Exception {
        try {
            stop(true);
        } finally {
            if (created) {
                server.execute("DROP DATABASE " + databaseName);
                created = false;
            }
        }
    }

    public static DatabaseMetadata mysqlDatabase(DatabaseMetadata server, String name) {
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Unsafe database name");
        String url = "jdbc:mysql://" + server.host() + ":" + server.port() + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=30000";
        return new DatabaseMetadata(url, server.username(), server.password(), server.host(), server.port());
    }
}
