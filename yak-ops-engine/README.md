# Yak Ops Engine

The engine accepts **Flink CDC 3.7 Pipeline YAML**, parses it to `PipelineDef`,
plans authorized source and sink bindings, translates the supported pipeline to
YakFlow's `StreamGraph`, and executes it. It does not parse API JSON or serialize
editor request bodies.

The DATA_SYNC Task Plugin owns the public V6 JSON-to-YAML conversion and task
parameters. The Backend API and frontend exchange JSON, never YAML. Engine-specific resource admission, schema preparation and checkpoint
ownership stay in Yak Ops; parser and definition semantics follow Flink CDC 3.7.

Unsupported Flink CDC operations must be rejected before submission rather than
accepted and silently discarded. Physical execution remains YakFlow, not Flink.
