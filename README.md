# Yak Ops

Yak Ops now contains two independent development modules:

- [yak-ops-cdc](yak-ops-cdc/): an embedded Flink 1.20.3 / Flink CDC pipeline codebase with CLI, composer, runtime, MySQL CDC source and JDBC sink.
- [yak-ops-ui](yak-ops-ui/): the existing frontend workspace, preserved without feature changes.

The former application backend, REST API, database layer, plugins, distribution, Docker deployment and business services have been removed. **The UI is not currently connected to a running Yak Ops backend.** The CDC CLI is independent of the UI.

## Requirements

- Java 21 and the checked-in Maven wrapper (the CDC subtree targets Java 17 and Flink 1.20.3).
- Node.js >= 22.13 and npm for the UI.

## Build CDC

```sh
bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package
```

Run the standalone CDC CLI from your IDE with its pipeline YAML and Flink runtime dependencies. The CDC subtree's upstream source and connector organization is unchanged.

## Develop UI

```sh
cd yak-ops-ui
npm ci
npm run dev
```

```sh
npm run format:check
npm run typecheck
npm run build
```

For verification and contributor guidance, see [DEVELOPMENT.md](DEVELOPMENT.md) and [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md). The [YOIP template](.github/YOIP_TEMPLATE.md) remains available.
