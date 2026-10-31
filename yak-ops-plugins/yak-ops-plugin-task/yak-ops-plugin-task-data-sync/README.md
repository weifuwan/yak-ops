# DATA_SYNC Task Plugin

The Task plugin owns the JSON API boundary and converts each validated definition into Pipeline YAML.

- `PipelineTaskChannelFactory` registers the stable `DATA_SYNC` Task type.
- `PipelineTaskChannel` validates definitions, derives query indexes, checks authorized references, and revalidates admission resources before composing deferred execution.
- `PipelineParameters` carries the immutable schema-version-7 JSON definition. Source and sink types are installed Connector factory identifiers such as `mysql`, `jdbc-mysql`, and `jdbc-postgres`.
- `PipelineYamlConverter` preserves source options, explicit table routes, transforms, and the selected BATCH or STREAMING mode.
- `PipelineConnectorCatalog` exposes installed Connector capabilities to the editor. Product display categories do not select the execution graph.
- `PipelineTask` adapts `PipelineExecution` to the Task lifecycle, including cancellation, terminal outcomes, physical job identity, and progress.

The engine module owns Pipeline definitions, resource planning, translators, schema preparation, and composition. YakFlow owns physical execution and checkpoints. A Task Instance must be durably registered before `PipelineExecution.start()`.

Previously persisted parameter versions are explicitly rejected. Existing definitions and checkpoints are never silently reinterpreted. Search projections are rebuilt from validated definitions; client-supplied indexes are ignored.
