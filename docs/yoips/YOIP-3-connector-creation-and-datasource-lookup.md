# YOIP-3: Connector creation and datasource lookup

## Status

Accepted. The repository owner approved the single-PR design and requested implementation in the Connector Factory + Datasource Access Simplification discussion on 2026-10-13.

## Motivation

Connector creation needs effective connection settings, operation settings and table metadata. Platform task identity and exclusive-resource admission belong to the task platform. Datasource metadata lookup and connection preparation share one Workspace-scoped service contract.

## Public interfaces

Factory creation contexts contain only immutable configuration and table metadata. Factories declare operation rules and complete creation rules; complete rules reuse operation rules and the shared connection rules. Definition validation cannot accept connection credentials or system-owned replication settings.

DataSourceLookupService in the datasource API owns Workspace-scoped information lookup, metadata lookup and version-checked connection preparation. DataSourceInfo and DataSourceTable contain no credentials. Connection settings are parsed from the same record whose revision is checked.

DataSourcePlugin prepares data-only connection options. The JDBC connection provider owns driver loading and SSH/Connection lifecycles. Source, Sink and Catalog reuse this provider. No connection, provider or service object is stored in configuration.

The Data Sync planner prepares stable MySQL replication parameters and the corresponding platform resource claim together. The Connector consumes the prepared server ID and topic prefix without importing Task API types.

## Compatibility

Repository-owned Factory, datasource lookup and plugin source signatures change together with all callers and tests. This is a source-level SPI change; independently compiled plugins must rebuild. No compatibility wrappers are introduced.

REST fields, persisted task JSON, datasource JSON, admission keys, datasource revisions, operator UIDs, checkpoint formats and the supported JDBC/CDC behavior remain unchanged. MySQL CDC continues to require direct connections. Effective connection settings are never written to task definitions, admission plans or user-visible diagnostics.

## Verification

Require strict staged option validation, Workspace isolation, pre-execution revision checks, identical admitted/executed CDC identity, independent connections, SSH cleanup, driver selection, credential-safe diagnostics and the existing execution acceptance tests. Existing Checkstyle, Spotless, architecture checks and CI gates remain enabled.

## Rejected alternatives

A generic Resource registry adds another protocol without a shared consumer capability. Injecting service objects into ReadonlyConfig hides ownership and cannot form a portable configuration contract. Moving datasource lookup into Connector factories reverses the platform boundary. Recreating driver or SSH behavior in each caller causes divergent lifecycles.
