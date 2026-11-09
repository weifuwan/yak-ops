# Upstream Sources

Yak Ops is an independent project whose backend scheduling and workflow implementation is derived in part from [Apache DolphinScheduler](https://github.com/apache/dolphinscheduler). It also incorporates adapted source from [Apache Flink CDC](https://github.com/apache/flink-cdc).

## Apache DolphinScheduler

- Project: https://github.com/apache/dolphinscheduler
- License: Apache License, Version 2.0
- Role in Yak Ops: scheduling, workflow orchestration, and related backend infrastructure
- Changes: adaptation to the Yak Ops module/package structure, integration with Yak Ops services and task plugins, and ongoing changes to the scheduling and workflow implementation
- Exact source version/commit: not recorded here; identify and verify the original baseline before claiming an exact version

The repository root [LICENSE](LICENSE) retains Apache License 2.0. The root [NOTICE](NOTICE) retains the upstream attribution notices. For modified upstream files, keep applicable original copyright and attribution headers and mark substantive modifications as required by Apache License 2.0.

## Apache Flink CDC

- Project: https://github.com/apache/flink-cdc
- License: Apache License, Version 2.0
- Role in Yak Ops: CDC pipeline runtime and connectors
- Migration details: [yak-ops-cdc/UPSTREAM.md](yak-ops-cdc/UPSTREAM.md)
- Included notices: [yak-ops-cdc/NOTICE](yak-ops-cdc/NOTICE)
- Included license: [yak-ops-cdc/LICENSE](yak-ops-cdc/LICENSE)

## Distribution

Before distributing source or binary releases, review the retained portions of each upstream project, their applicable NOTICE and copyright statements, modified-file notices, and licenses of bundled dependencies and other redistributed assets. This document describes code provenance; it does not grant rights to third-party trademarks or replace the actual license texts.
