# Flink CDC 3.7 source migration

Upstream: https://github.com/apache/flink-cdc/tree/release-3.7.0

Source and test code is copied from Apache Flink CDC 3.7.0 and keeps its original Java packages. Maven module names, local dependency coordinates and reactor module lists are adapted for Yak Ops.

Only MySQL CDC connectors and their Debezium/Base dependencies are included. No other database connectors, Flink 2 compatibility, distribution, optional AI model client implementations, Python bindings or E2E harness are copied. The Dummy Model and UDF example modules remain only to support upstream pipeline tests.

Baseline: Flink 1.20.3, Debezium 1.9.8.Final. Yak Ops application/task integration intentionally deferred.

Build from repository root: \`mvn -pl yak-ops-cdc -am -Dmaven.test.skip=true compile\`

JDBC Pipeline Sink is separately ported from the unmerged Apache Flink CDC PR #3916
(`d8bf6f37001bd67b48174456a57e484d9078f040`). The JDBC connector dependency
uses JDBC `3.3.0-1.20` to match Yak Ops Flink 1.20.3, with Maven coordinates remapped to Yak Ops.
The original PR uses JDBC `3.2.0-1.19`; Yak Ops tests the Flink 1.20 connector in its backend CDC suite.
