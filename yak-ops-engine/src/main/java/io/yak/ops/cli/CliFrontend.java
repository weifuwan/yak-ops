package io.yak.ops.cli;

import io.yak.ops.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.composer.PipelineContext;
import io.yak.ops.composer.PipelineExecution;
import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.PipelineDef;
import io.yak.ops.composer.execution.IncrementalBatchPreparation;
import io.yak.ops.composer.yakflow.EnvironmentOptions;
import io.yak.ops.composer.yakflow.ExecutionConfigurationFactory;
import io.yak.ops.composer.yakflow.YakFlowPipelineComposer;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.flow.runtime.execution.EmbeddedPipelineExecutor;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.flink.cdc.common.pipeline.PipelineOptions;
import org.apache.flink.cdc.common.pipeline.RuntimeExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs a standalone YAML Pipeline through the same Composer and YakFlow execution as embedded callers. */
public final class CliFrontend {
    private static final Logger LOG = LoggerFactory.getLogger(CliFrontend.class);

    private CliFrontend() {}

    /**
     * Starts one local Pipeline execution and exits with its terminal result.
     *
     * @param args YAML path and optional runtime-mode/state-directory overrides
     */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            Arguments arguments = Arguments.parse(args);
            if (arguments.help) {
                printHelp(out);
                return 0;
            }
            PipelineDef definition = parseDefinition(arguments);
            String executionId = UUID.randomUUID().toString();
            String workspaceId = "cli-" + executionId;
            Path stateRoot = arguments.stateDirectory == null
                    ? Files.createTempDirectory("yakflow-cli-")
                    : Files.createDirectories(
                            arguments.stateDirectory.toAbsolutePath().normalize());
            FactoryUtil factories = new FactoryUtil();
            CliDataSourceLookupService lookup =
                    new CliDataSourceLookupService(definition, factories, workspaceId, executionId);
            PipelineDef admitted = lookup.resolve(definition);
            ReadonlyConfig environment = EnvironmentOptions.defaults();
            YakFlowPipelineComposer composer = new YakFlowPipelineComposer(
                    lookup,
                    factories,
                    environment,
                    new ExecutionConfigurationFactory(environment, stateRoot),
                    lookup.sourceConnectorOptions(),
                    lookup.sinkConnectorOptions(),
                    new EmbeddedPipelineExecutor(),
                    new IncrementalBatchPreparation(lookup, (identity, proposed) -> {
                        throw new UnsupportedOperationException(
                                "CLI incremental JDBC execution requires a durable cursor store");
                    }));
            PipelineContext context = new PipelineContext(workspaceId, "cli-pipeline", 1, executionId, false);
            PipelinePlan plan = composer.plan(admitted, context);
            PipelineExecution execution = composer.compose(admitted, plan, context);
            out.println("Execution ID: " + executionId);
            out.println("State directory: " + stateRoot.resolve(executionId));
            JobStatus status = execute(execution);
            out.println("Pipeline status: " + status);
            return status == JobStatus.FINISHED ? 0 : status == JobStatus.CANCELED ? 130 : 1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            err.println("Pipeline canceled after interruption.");
            return 130;
        } catch (Exception failure) {
            // YAML, URLs, vendor errors and exception causes can contain credentials or row values.
            err.println("Pipeline could not be started or completed ("
                    + failure.getClass().getSimpleName() + ").");
            err.println("Check the YAML, connection permissions and connector options; use --help for CLI usage.");
            return 1;
        }
    }

    static PipelineDef parseDefinition(Arguments arguments) throws Exception {
        PipelineDef definition = new YamlPipelineDefinitionParser().parse(Files.readString(arguments.pipelineFile));
        if (arguments.runtimeMode != null) {
            definition.getConfig().set(PipelineOptions.PIPELINE_EXECUTION_RUNTIME_MODE, arguments.runtimeMode);
        }
        return definition;
    }

    /** Keeps the process and its shutdown hook alive until physical termination has been observed. */
    static JobStatus execute(PipelineExecution execution) throws InterruptedException, ExecutionException {
        Thread shutdown = Thread.ofPlatform().name("yakflow-cli-shutdown").unstarted(() -> {
            try {
                cancelAndAwait(execution);
            } catch (RuntimeException failure) {
                LOG.warn("Pipeline shutdown did not confirm terminal execution state");
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            execution.start();
            return execution.completion().toCompletableFuture().get();
        } catch (InterruptedException interrupted) {
            try {
                cancelAndAwait(execution);
            } catch (RuntimeException cancellationFailure) {
                interrupted.addSuppressed(cancellationFailure);
            }
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (RuntimeException | ExecutionException failure) {
            try {
                cancelAndAwait(execution);
            } catch (RuntimeException cancellationFailure) {
                if (failure != cancellationFailure) failure.addSuppressed(cancellationFailure);
            }
            throw failure;
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (IllegalStateException shuttingDown) {
                // The registered hook owns cancellation once JVM shutdown has begun.
            }
        }
    }

    private static void cancelAndAwait(PipelineExecution execution) {
        RuntimeException cancellationFailure = null;
        try {
            execution.cancel().toCompletableFuture().join();
        } catch (RuntimeException failure) {
            cancellationFailure = failure;
        }
        try {
            execution.completion().toCompletableFuture().join();
        } catch (RuntimeException completionFailure) {
            if (cancellationFailure != null && cancellationFailure != completionFailure) {
                completionFailure.addSuppressed(cancellationFailure);
            }
            throw completionFailure;
        }
        if (cancellationFailure != null) throw cancellationFailure;
    }

    private static void printHelp(PrintStream out) {
        out.println("Usage: CliFrontend [--runtime-mode BATCH|STREAMING] [--state-dir DIR] pipeline.yaml");
        out.println("Runs a new local execution with a unique identity and retained checkpoint directory.");
        out.println("MySQL endpoints accept hostname, port (default 3306), username and password.");
        out.println("JDBC endpoints also accept connection.url and the connector's connection.* settings.");
        out.println(
                "Source tables and tables.exclude use Flink CDC selectors and are frozen at startup (maximum 100).");
        out.println("Routes must identify full target namespaces; JDBC sinks require an explicit write.mode.");
        out.println(
                "Use BATCH for JDBC full copies, or mysql with scan.startup.mode: snapshot; use STREAMING for CDC.");
        out.println("Saved platform datasource-id references and bounded JDBC cursor batches are not accepted.");
        out.println("Ctrl-C cancels the Pipeline and waits for its physical termination.");
    }

    static final class Arguments {
        private final Path pipelineFile;
        private final Path stateDirectory;
        private final RuntimeExecutionMode runtimeMode;
        private final boolean help;

        private Arguments(Path pipelineFile, Path stateDirectory, RuntimeExecutionMode runtimeMode, boolean help) {
            this.pipelineFile = pipelineFile;
            this.stateDirectory = stateDirectory;
            this.runtimeMode = runtimeMode;
            this.help = help;
        }

        static Arguments parse(String[] args) {
            Path file = null;
            Path state = null;
            RuntimeExecutionMode mode = null;
            boolean help = args.length == 0;
            Set<String> supplied = new HashSet<>();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--help".equals(argument) || "-h".equals(argument)) {
                    help = true;
                } else if ("--runtime-mode".equals(argument) || "--state-dir".equals(argument)) {
                    if (!supplied.add(argument) || ++index == args.length || args[index].startsWith("--")) {
                        throw new IllegalArgumentException("A CLI option is repeated or has no value");
                    }
                    if ("--runtime-mode".equals(argument)) {
                        mode = RuntimeExecutionMode.valueOf(args[index].toUpperCase(Locale.ROOT));
                    } else {
                        state = Path.of(args[index]);
                    }
                } else if (argument.startsWith("-") || file != null) {
                    throw new IllegalArgumentException("Unknown CLI option or more than one Pipeline file");
                } else {
                    file = Path.of(argument);
                }
            }
            if (!help && file == null) throw new IllegalArgumentException("A Pipeline YAML file is required");
            return new Arguments(file, state, mode, help);
        }
    }
}
