# Developing Yak Ops

This repository is intentionally limited to the independent `yak-ops-cdc` and `yak-ops-ui` modules. The previous Yak Ops backend, deployment, database migration, plugins and distribution were removed.

## CDC

Use Java 21 and the Maven wrapper at the repository root. The CDC module itself uses its own Java 17 and Flink 1.20.3 settings.

```sh
# Compile CLI, composer, runtime and required connectors without running tests
bash mvnw -B -ntp -pl :yak-ops-cdc-cli -am -DskipTests package

# Check formatting of all retained CDC Java modules
bash mvnw -B -ntp -pl '!yak-ops-ui' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

CDC integration checks and actual source-to-sink execution require the appropriate external MySQL environment and Flink runtime libraries. Compilation does not establish that an end-to-end pipeline succeeds. The manual CDC compile workflow may be triggered in GitHub Actions.

## UI

Use Node.js 22.13 or later and npm:

```sh
cd yak-ops-ui
npm ci
npm run format:check
npm run typecheck
npm run build
npm run dev
```

The UI source remains intact but was originally designed for the deleted backend API. Do not interpret successful UI compilation as a working product integration.

## Review

Record which commands actually ran and any skipped or blocked checks. Keep migrated Flink CDC source compatibility and upstream attribution. There is no longer a native Yak Ops Business test suite or application distribution.
