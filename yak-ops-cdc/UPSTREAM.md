# Flink CDC 3.7 source migration

Upstream: https://github.com/apache/flink-cdc/tree/release-3.7.0

Source and test code is copied from Apache Flink CDC 3.7.0 and keeps its original Java packages. Maven module names, local dependency coordinates and reactor module lists are adapted for Yak Ops.

Only MySQL CDC connectors and their Debezium/Base dependencies are included. No other database connectors, Flink 2 compatibility, distribution, optional AI model client implementations, Python bindings or E2E harness are copied. The Dummy Model and UDF example modules remain only to support upstream pipeline tests.

Baseline: Flink 1.20.3, Debezium 1.9.8.Final. Yak Ops application/task integration intentionally deferred.

Build from repository root: \`mvn -pl yak-ops-cdc -am -Dmaven.test.skip=true compile\`
