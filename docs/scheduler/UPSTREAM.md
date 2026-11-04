# Apache DolphinScheduler Source Migration

Pinned upstream: apache/dolphinscheduler 3.4.3-release commit 26228f9c5bff24b3ded7095484caf7a5ecffa050

Upstream Maven version: 3.4.4-SNAPSHOT; Yak Ops Maven version: 1.4.0.

Core Java sources, tests and resources are unchanged from upstream. Maven POM
coordinates, module names and excluded optional plugin dependencies are adapted.
For a SHA256 inventory and exclusion list, see MIGRATION_MANIFEST.json.
Upstream parent POM retained as UPSTREAM_ROOT_POM.xml. Apache LICENSE, NOTICE
and upstream source headers remain intact. Compilation and runtime require
separate verification; source import does not establish runtime correctness.
