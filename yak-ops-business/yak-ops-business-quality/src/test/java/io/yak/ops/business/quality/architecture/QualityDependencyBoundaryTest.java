package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for the final Data Quality package graph and narrow corridors. */
class QualityDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.quality";
  private static final Pattern QUALITY_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(?:static\\s+)?("
              + Pattern.quote(BASE)
              + "\\.([A-Za-z0-9_]+)\\.[^;]+);");
  private static final Pattern DATASOURCE_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(io\\.yak\\.ops\\.business\\.datasource\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(
              "controller",
              Set.of("asset", "config", "domain", "execution", "monitor", "template", "workspace")),
          Map.entry("workspace", Set.of("config", "domain", "monitor", "repository")),
          Map.entry("monitor", Set.of("config", "domain", "repository", "schedule", "task")),
          Map.entry("task", Set.of("config", "domain", "execution", "repository")),
          Map.entry("schedule", Set.of("config", "domain", "execution", "repository")),
          Map.entry("execution", Set.of("alert", "config", "domain", "gateway", "repository")),
          Map.entry("alert", Set.of("config", "domain", "repository")),
          Map.entry("asset", Set.of("config", "domain", "gateway", "repository")),
          Map.entry("template", Set.of("config", "domain", "repository")),
          Map.entry("gateway", Set.of("config")),
          Map.entry("repository", Set.of("config", "dao", "domain")),
          Map.entry("dao", Set.of()),
          Map.entry("domain", Set.of()),
          Map.entry("config", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : qualityDependencies()) {
      Set<String> allowed =
          ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredTopLevelDependencyGraphIsAcyclic() {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared Quality dependency graph");
  }

  @Test
  void actualTopLevelDependencyGraphIsAcyclic() throws IOException {
    Map<String, Set<String>> graph = new HashMap<>();
    ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet().forEach(key -> graph.put(key, new HashSet<>()));
    for (Dependency dependency : qualityDependencies()) {
      graph.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(graph, "actual Quality import graph");
  }

  @Test
  void crossSubsystemCorridorsStayNarrow() throws IOException {
    assertExactCorridor(
        "monitor",
        "schedule",
        Set.of(
            BASE + ".schedule.QualityScheduleLifecycle",
            BASE + ".schedule.QualityScheduleCalculator"));
    assertExactCorridor(
        "monitor",
        "task",
        Set.of(BASE + ".task.QualityTaskPublisher"));
    assertExactCorridor(
        "task",
        "execution",
        Set.of(
            BASE + ".execution.QualityExecutionManager",
            BASE + ".execution.QualityExecutionPlanFactory",
            BASE + ".execution.QualityExecutionReader",
            BASE + ".execution.QualityExecutionReceipt"));
    assertExactCorridor(
        "schedule",
        "execution",
        Set.of(BASE + ".execution.QualityExecutionManager"));
    assertExactCorridor(
        "workspace",
        "monitor",
        Set.of(BASE + ".monitor.QualityMonitorReader"));
    assertExactCorridor(
        "execution",
        "alert",
        Set.of(BASE + ".alert.QualityAlertRecorder"));

    assertGatewayCorridor("asset");
    assertGatewayCorridor("execution");
  }

  @Test
  void datasourceDependencyIsIsolatedBehindDeclaredBoundaries() throws IOException {
    Map<String, Set<String>> allowed =
        Map.of(
            "config/QualityConfiguration.java",
            Set.of(
                "io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration"),
            "gateway/datasource/DataSourceQualityCatalogAdapter.java",
            Set.of(
                "io.yak.ops.business.datasource.catalog.DataSourceCatalogReader",
                "io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest",
                "io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode"));

    for (ExternalDependency dependency : datasourceDependencies()) {
      assertThat(allowed)
          .as("Unexpected Datasource dependency from %s", dependency.relativePath())
          .containsKey(dependency.relativePath());
      assertThat(allowed.get(dependency.relativePath()))
          .as("Unexpected Datasource type imported by %s", dependency.relativePath())
          .contains(dependency.importedType());
    }
  }

  @Test
  void bottomLayersDoNotPointBackToApplicationSubsystems() throws IOException {
    for (Dependency dependency : qualityDependencies()) {
      if (dependency.sourcePackage().equals("domain")) {
        throw new AssertionError("Quality Domain must not import upper Quality packages: " + dependency);
      }
      if (dependency.sourcePackage().equals("dao")) {
        throw new AssertionError("Quality DAO must not import Quality business packages: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below application roles: %s", dependency)
            .isIn("config", "dao", "domain");
      }
      if (dependency.sourcePackage().equals("config")) {
        throw new AssertionError("Quality config must not import Quality business roles: " + dependency);
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden : Set.of("service", "common", "helper", "utils", "util", "base")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Quality business bucket '%s' must not exist", forbidden)
          .isFalse();
    }
  }

  private void assertExactCorridor(String source, String target, Set<String> allowedTypes)
      throws IOException {
    for (Dependency dependency : crossing(source, target)) {
      assertThat(allowedTypes)
          .as("Unexpected %s -> %s corridor in %s", source, target, dependency.relativePath())
          .contains(dependency.importedType());
    }
  }

  private void assertGatewayCorridor(String source) throws IOException {
    String allowedPrefix = BASE + ".gateway.datasource.QualityDataCatalogGateway";
    for (Dependency dependency : crossing(source, "gateway")) {
      assertThat(dependency.importedType())
          .as("%s must use the Quality-owned Datasource gateway", dependency.relativePath())
          .startsWith(allowedPrefix);
    }
  }

  private List<Dependency> crossing(String source, String target) throws IOException {
    return qualityDependencies().stream()
        .filter(dependency -> dependency.sourcePackage().equals(source))
        .filter(dependency -> dependency.targetPackage().equals(target))
        .toList();
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> nodes = new HashSet<>(graph.keySet());
    graph.values().forEach(nodes::addAll);
    Map<String, Integer> indegree = new HashMap<>();
    Map<String, Set<String>> reverse = new HashMap<>();
    nodes.forEach(node -> indegree.put(node, 0));

    for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
      for (String target : entry.getValue()) {
        indegree.compute(entry.getKey(), (ignored, value) -> value + 1);
        reverse.computeIfAbsent(target, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }

    ArrayDeque<String> ready = new ArrayDeque<>();
    indegree.forEach(
        (node, degree) -> {
          if (degree == 0) {
            ready.add(node);
          }
        });

    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = indegree.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) {
          ready.add(dependent);
        }
      }
    }

    assertThat(visited).as("%s must remain acyclic", label).isEqualTo(nodes.size());
  }

  private List<Dependency> qualityDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) {
          continue;
        }
        String sourcePackage = relative.getName(0).toString();
        Matcher matcher = QUALITY_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          String importedType = matcher.group(1);
          String targetPackage = matcher.group(2);
          if (!sourcePackage.equals(targetPackage)) {
            result.add(
                new Dependency(
                    sourcePackage, targetPackage, importedType, normalize(relative)));
          }
        }
      }
    }
    return result;
  }

  private List<ExternalDependency> datasourceDependencies() throws IOException {
    Path root = productionRoot();
    List<ExternalDependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        Matcher matcher = DATASOURCE_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          result.add(new ExternalDependency(normalize(relative), matcher.group(1)));
        }
      }
    }
    return result;
  }

  private Path productionRoot() {
    Path moduleLocal = Path.of("src/main/java/io/yak/ops/business/quality");
    if (Files.isDirectory(moduleLocal)) {
      return moduleLocal;
    }
    Path repositoryRelative =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-quality",
            "src",
            "main",
            "java",
            "io",
            "yak",
            "ops",
            "business",
            "quality");
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Data Quality production source root")
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record Dependency(
      String sourcePackage,
      String targetPackage,
      String importedType,
      String relativePath) {}

  private record ExternalDependency(String relativePath, String importedType) {}
}
