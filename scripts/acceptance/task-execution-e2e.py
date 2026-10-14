#!/usr/bin/env python3
"""Run product-level Task execution against disposable MySQL and PostgreSQL containers."""

import http.cookiejar
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import uuid


BASE = "http://127.0.0.1:18080"
STATE_ROOT = Path("/tmp/yak-task-state")
BOOT_LOG = Path("/tmp/yak-task-e2e-output.log")
JAR = Path("yak-ops-boot/target/yak-ops-boot-1.2.0.jar")
APP = None
DB_PASSWORD = os.environ["E2E_DB_PASSWORD"]
USER_PASSWORD = os.environ["E2E_USER_PASSWORD"]


class Client:
    def __init__(self, workspace=None):
        self.workspace = workspace
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, payload=None, headers=None, status=200):
        fields = {"Content-Type": "application/json"}
        if self.workspace:
            fields["X-Workspace-Id"] = self.workspace
        if headers:
            fields.update(headers)
        encoded = None if payload is None else json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            BASE + path, data=encoded, headers=fields, method=method)
        try:
            with self.opener.open(req, timeout=20) as response:
                received = response.status
                raw = response.read()
        except urllib.error.HTTPError as error:
            received = error.code
            raw = error.read()
        result = json.loads(raw) if raw else {}
        if received != status:
            raise AssertionError(
                f"{method} {path}: HTTP {received} expected {status}, response={result}")
        if received < 300 and result.get("code") != 200:
            raise AssertionError(f"{method} {path}: rejected response={result}")
        return result.get("data")

    def download(self, path):
        headers = {}
        if self.workspace:
            headers["X-Workspace-Id"] = self.workspace
        request = urllib.request.Request(BASE + path, headers=headers)
        with self.opener.open(request, timeout=30) as response:
            assert response.status == 200
            return response.read()

    def login(self):
        self.call(
            "POST", "/api/v1/account/login",
            {"userName": "taske2e", "pw": USER_PASSWORD})


def query_mysql(sql):
    return subprocess.check_output(
        ["docker", "exec", "yak-task-mysql",
         "mysql", "-uroot", "-p" + DB_PASSWORD, "-N", "-B", "-e", sql],
        text=True, stderr=subprocess.DEVNULL).strip()


def query_postgres(sql):
    return subprocess.check_output(
        ["docker", "exec", "-e", "PGPASSWORD=" + DB_PASSWORD,
         "yak-task-postgres", "psql", "-U", "yak", "-d", "ods", "-Atc", sql],
        text=True, stderr=subprocess.DEVNULL).strip()


def until(description, evaluate, accepted, timeout=120):
    stop = time.monotonic() + timeout
    observed = None
    while time.monotonic() < stop:
        try:
            observed = evaluate()
            if accepted(observed):
                return observed
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    if "Task " in description and APP is not None and APP.poll() is None:
        dump_threads(APP)
    raise AssertionError(f"{description} timed out; last={observed}")


def dump_threads(app):
    target = Path("/tmp/yak-task-e2e-threads.txt")
    try:
        subprocess.run(
            ["jcmd", str(app.pid), "Thread.dump_to_file", "-overwrite",
             "-format=text", str(target)],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=20)
        dump = target.read_text(encoding="utf-8")
        for segment in dump.split("\n\n"):
            if any(word in segment for word in (
                    "yak-local-job", "yakflow", "source-coordinator", "SourceCoordinator",
                    "StreamTask", "Debezium", "task-dispatch")):
                print("THREAD_DUMP " + segment[:2400], flush=True)
    except (OSError, subprocess.SubprocessError) as error:
        print(f"Unable to capture JVM thread state: {error}", flush=True)


def boot_environment(port):
    environment = os.environ.copy()
    environment.update({
        "SERVER_PORT": str(port),
        "YAK_DATABASE_URL": (
            "jdbc:mysql://127.0.0.1:3306/yak_ops"
            "?useSSL=false&allowPublicKeyRetrieval=true"),
        "YAK_DATABASE_USERNAME": "root",
        "YAK_DATABASE_PASSWORD": DB_PASSWORD,
        "YAK_SECURITY_BOOTSTRAP_ENABLED": "true",
        "YAK_SECURITY_BOOTSTRAP_USERNAME": "taske2e",
        "YAK_SECURITY_BOOTSTRAP_PASSWORD": USER_PASSWORD,
        "YAK_SECURITY_BOOTSTRAP_REAL_NAME": "Task acceptance",
        "YAK_LOG_FILE": "/tmp/yak-task-e2e-application.log",
    })
    return environment


def boot():
    global APP
    environment = boot_environment(18080)
    with BOOT_LOG.open("a", encoding="utf-8") as output:
        process = subprocess.Popen(
            ["java", "-jar", str(JAR),
             "--yak.task.execution.state-directory=" + str(STATE_ROOT)],
            stdout=output, stderr=subprocess.STDOUT, env=environment)

    def ready():
        if process.poll() is not None:
            raise AssertionError("Yak Ops exited during startup; inspect acceptance log")
        with urllib.request.urlopen(BASE + "/v3/api-docs", timeout=4) as response:
            return response.status
    until("Yak Ops readiness", ready, lambda status: status == 200, timeout=150)
    APP = process
    return process


def shutdown(process, force=False):
    if process.poll() is not None:
        return
    if force:
        process.kill()
    else:
        process.terminate()
    try:
        process.wait(timeout=25)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def verify_single_embedded_owner():
    log = Path("/tmp/yak-task-e2e-rejected-owner.log")
    with log.open("w", encoding="utf-8") as output:
        contender = subprocess.Popen(
            ["java", "-jar", str(JAR),
             "--yak.task.execution.state-directory=" + str(STATE_ROOT)],
            stdout=output, stderr=subprocess.STDOUT,
            env=boot_environment(18081))
    try:
        until(
            "Concurrent embedded execution owner rejected",
            contender.poll,
            lambda code: code is not None,
            timeout=40)
        evidence = log.read_text(encoding="utf-8")
        if "Another Yak Ops embedded executor owns the local state directory" not in evidence:
            raise AssertionError("Second Boot process was not rejected by its state-root owner lock")
    finally:
        shutdown(contender, force=True)


def create_datasource(client, name, kind, database, username, port):
    client.call("POST", "/api/v1/data-source", {
        "name": name, "dbType": kind, "environment": "TEST",
        "connectionParams": {
            "host": "127.0.0.1", "port": port, "database": database,
            "username": username, "password": DB_PASSWORD,
        },
    })
    page = client.call("POST", "/api/v1/data-source/page", {
        "pageNo": 1, "pageSize": 100, "sorts": []})
    found = [item["id"] for item in page["bizData"] if item["name"] == name]
    if len(found) != 1:
        raise AssertionError(f"Unexpected datasource lookup for {name}")
    return found[0]


def create_task(client, name, source, target, streaming, debug=False, sink_table="ods_orders"):
    params = {
        "solutionType": "SINGLE_TABLE_REALTIME" if streaming else "SINGLE_TABLE_OFFLINE",
        "steps": ["FULL", "INCREMENTAL"] if streaming else ["FULL"],
        "source": {"datasourceId": source},
        "sink": {"datasourceId": target},
        "tableSelection": {"mode": "EXPLICIT"},
        "tableMappings": [{"source": {"table": "orders"},
                           "target": {"schema": "public", "table": sink_table},
                           "writeMode": "UPSERT" if streaming else "APPEND"}],
    }
    definition = client.call("POST", "/api/v1/task-definitions", {
        "name": name, "taskType": "DATA_SYNC", "debug": debug,
        "parameters": json.dumps(params, separators=(",", ":")),
    })["id"]
    client.call("POST", f"/api/v1/task-definitions/{definition}/publish")
    return definition


def verify_parameter_versions(client, source, target, existing_v3):
    """Seed a historical version-two fixture in disposable storage, then use real public operations."""
    query_postgres("CREATE TABLE public.version_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    payload = {
        "solutionType": "SINGLE_TABLE_OFFLINE", "steps": ["FULL"],
        "source": {"datasourceId": source}, "sink": {"datasourceId": target},
        "tableSelection": {"mode": "EXPLICIT"}, "tableMappings": [{
            "source": {"table": "orders"}, "target": {"schema": "public", "table": "version_orders"},
            "writeMode": "UPSERT"}],
    }
    old = {"source": {"type": "JDBC", "datasourceId": source, "table": "orders"}, "transform": [],
           "sink": {"type": "JDBC", "datasourceId": target, "schema": "public", "table": "version_orders", "writeMode": "UPSERT"}}
    definition = client.call("POST", "/api/v1/task-definitions", {
        "name": "version-contract-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC",
        "parameters": json.dumps(payload)})
    identity = definition["id"]
    assert re.fullmatch(r"[A-Za-z0-9_-]{8,64}", identity)
    # Only fixture setup writes historical storage; no production endpoint can mutate a Version.
    encoded = json.dumps(old, separators=(",", ":")).encode().hex()
    query_mysql("UPDATE yak_ops.yak_ops_task_definition_version SET parameter_schema_version=2, parameters=CONVERT(0x"
                + encoded + " USING utf8mb4) WHERE definition_id='" + identity + "' AND version=1")
    assert client.call("GET", "/api/v1/task-definitions/" + identity)["parameterSchemaVersion"] == 2
    client.call("POST", f"/api/v1/task-definitions/{identity}/publish")
    first, _ = submit(client, identity)
    wait_status(client, first["instanceId"], {"FINISHED"})
    snapshot = client.call("GET", f"/api/v1/task-instances/{first['instanceId']}/definition")
    assert snapshot["name"] == definition["name"]
    assert snapshot["parameterSchemaVersion"] == 2 and json.loads(snapshot["parameters"]) == old
    filtered = client.call("POST", "/api/v1/task-definitions/page", {
        "taskType": "DATA_SYNC", "pageNo": 1, "pageSize": 200,
        "parameterFilters": {"solutionType": "SINGLE_TABLE_OFFLINE"}})
    assert {identity, existing_v3}.issubset({row["id"] for row in filtered["bizData"]})
    client.call("POST", f"/api/v1/task-definitions/{identity}/unpublish")
    metadata = client.call("PUT", f"/api/v1/task-definitions/{identity}", {"name": definition["name"] + "-renamed", "debug": True})
    assert metadata["definitionVersion"] == 1 and metadata["parameterSchemaVersion"] == 2
    migrated = client.call("PUT", f"/api/v1/task-definitions/{identity}", {
        "name": metadata["name"], "debug": True, "parameters": json.dumps(payload)})
    assert migrated["definitionVersion"] == 2 and migrated["parameterSchemaVersion"] == 3
    # The display name follows current metadata; every pinned execution field remains unchanged.
    historical = client.call("GET", f"/api/v1/task-instances/{first['instanceId']}/definition")
    assert historical == {**snapshot, "name": metadata["name"]}
    client.call("POST", f"/api/v1/task-definitions/{identity}/publish")
    second, _ = submit(client, identity)
    wait_status(client, second["instanceId"], {"FINISHED"})
    assert query_postgres("SELECT COUNT(*) FROM public.version_orders") == "2"
    # New-write and step constraints are enforced by the backend, not only by disabled checkboxes.
    client.call("POST", "/api/v1/task-definitions", {
        "name": "reject-v2-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC", "parameters": json.dumps(old)}, status=400)
    for changes in ({"steps": []}, {"steps": ["SCHEMA", "FULL"]}, {"tableMappings": []},
                    {"source": {"datasourceId": source, "type": "JDBC"}}):
        client.call("POST", "/api/v1/task-definitions", {
            "name": "reject-solution-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC",
            "parameters": json.dumps({**payload, **changes})}, status=400)


def submit(client, definition, key=None, expected=202):
    key = key or uuid.uuid4().hex
    result = client.call(
        "POST", f"/api/v1/task-definitions/{definition}/instances",
        headers={"Idempotency-Key": key}, status=expected)
    return result, key


def status(client, identity):
    return client.call("GET", f"/api/v1/task-instances/{identity}")["status"]


def wait_status(client, identity, allowed, timeout=150):
    def acceptable(current):
        if current in {"FAILED", "FINISHED", "CANCELED"} and current not in allowed:
            raise AssertionError(f"Task {identity} ended in unexpected state {current}")
        return current in allowed
    return until(
        f"Task {identity} status", lambda: status(client, identity), acceptable, timeout)


def claim_count(identity):
    if not re.fullmatch(r"[0-9A-Za-z_-]{8,64}", identity):
        raise AssertionError("Unexpected Instance identifier")
    return int(query_mysql(
        "SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim "
        + f"WHERE instance_id = '{identity}'"))


def verify_schedule_configuration(client, definition):
    """Exercise the real Schedule service and MySQL schema without activating a scheduler."""
    base = "/api/v1/schedules"
    payload = {"targetType": "TASK", "targetId": definition,
               "cronExpression": "0 * * * * ?", "timeZone": "UTC"}
    created = client.call("POST", base, payload)
    identity = created["id"]
    assert created["enabled"] is False and created["revision"] == 1
    assert created["schedulable"] is True
    assert created["registrationStatus"] == "APPLIED"
    assert created.get("nextFireTime") is None
    client.call("POST", base, payload, status=409)
    client.call("POST", base, dict(payload, targetType="WORKFLOW"), status=400)
    preview = client.call("POST", base + "/preview", {
        "cronExpression": "0 0 8 * * ?", "timeZone": "Asia/Shanghai",
        "after": "2026-10-13T00:00:00Z"})
    assert preview["nextFireTimes"][0] == "2026-10-14T00:00:00Z"
    assert len(preview["nextFireTimes"]) == 5
    client.call("POST", base + "/preview", {
        "cronExpression": "0 * * * * ?", "timeZone": "Asia/Invalid"}, status=400)
    page = client.call("POST", base + "/page", {"targetId": definition, "pageNo": 1, "pageSize": 20})
    assert page["pagination"]["total"] == 1 and page["bizData"][0]["id"] == identity
    batch = {"targetType": "TASK", "targetIds": [definition, "missing"], "pageNo": 1, "pageSize": 200}
    result = client.call("POST", base + "/page", batch)
    assert result["pagination"]["total"] == 1 and result["bizData"][0]["id"] == identity
    assert client.call("POST", base + "/page", dict(batch, targetIds=["missing"]))["pagination"]["total"] == 0
    for ids in ([], [" "], ["x" * 65], [definition] * 201):
        client.call("POST", base + "/page", dict(batch, targetIds=ids), status=400)
    client.call("POST", base + "/page", dict(batch, targetId=definition), status=400)
    same = client.call("PUT", base + "/" + identity, {
        "revision": 1, "cronExpression": payload["cronExpression"], "timeZone": "UTC"})
    assert same["revision"] == 1
    changed = client.call("PUT", base + "/" + identity, {
        "revision": 1, "cronExpression": "0 0 8 * * ?", "timeZone": "Asia/Shanghai"})
    assert changed["revision"] == 2 and changed["enabled"] is False
    client.call("PUT", base + "/" + identity, {
        "revision": 1, "cronExpression": payload["cronExpression"], "timeZone": "UTC"}, status=409)
    workspace = client.workspace
    client.workspace = client.call("POST", "/api/v1/workspaces", {"name": "schedule-" + uuid.uuid4().hex[:8]})["id"]
    try:
        assert client.call("POST", base + "/page", batch)["pagination"]["total"] == 0
        client.call("GET", base + "/" + identity, status=404)
        client.call("DELETE", base + "/" + identity + "?revision=2", status=404)
        client.call("POST", base, payload, status=404)
    finally:
        client.workspace = workspace
    client.call("DELETE", base + "/" + identity + "?revision=2")
    restored = client.call("POST", base, payload)
    assert restored["id"] != identity and restored["enabled"] is False
    assert query_mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_schedule WHERE id='" + identity + "' AND deleted_at IS NOT NULL") == "1"
    # The database, not just a pre-insert service query, owns target uniqueness.
    duplicate_sql = ("INSERT INTO yak_ops.yak_ops_schedule (id,workspace_id,target_type,target_id,cron_expression,time_zone,"
                     "create_time,update_time,create_by,update_by) SELECT 'duplicate-schedule',workspace_id,target_type,target_id,"
                     "cron_expression,time_zone,create_time,update_time,create_by,update_by FROM yak_ops.yak_ops_schedule WHERE id='" + restored["id"] + "'")
    try:
        query_mysql(duplicate_sql)
    except subprocess.CalledProcessError:
        pass
    else:
        raise AssertionError("MySQL allowed duplicate live schedule target")
    return restored["id"]



def fire_page(client, schedule):
    return client.call("POST", f"/api/v1/schedules/{schedule}/fires/page", {
        "pageNo": 1, "pageSize": 100})


def verify_periodic_jdbc(client, source, target):
    """Require an actual Quartz callback through admission, dispatch and a physical JDBC transfer."""
    query_postgres("CREATE TABLE public.scheduled_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    definition = create_task(client, "scheduled-" + uuid.uuid4().hex[:8], source, target, False,
                             debug=True, sink_table="scheduled_orders")
    schedule = client.call("POST", "/api/v1/schedules", {
        "targetType": "TASK", "targetId": definition, "cronExpression": "0/10 * * * * ?", "timeZone": "UTC"})
    identity = schedule["id"]
    enabled = client.call("POST", f"/api/v1/schedules/{identity}/enable", {"revision": 1})
    assert enabled["enabled"] and enabled["revision"] == 2
    until("Quartz registration", lambda: client.call("GET", f"/api/v1/schedules/{identity}"),
          lambda value: value["registrationStatus"] == "APPLIED", timeout=45)
    page = until("Scheduled JDBC admission", lambda: fire_page(client, identity),
                 lambda value: any(row["outcome"] == "ADMITTED" for row in value["bizData"]), timeout=60)
    stopped = client.call("POST", f"/api/v1/schedules/{identity}/disable", {"revision": 2})
    assert not stopped["enabled"] and stopped["revision"] == 3
    # Repeating a stopped intent is harmless, even when the supplied prior revision is stale.
    assert client.call("POST", f"/api/v1/schedules/{identity}/disable", {"revision": 2})["revision"] == 3
    fire = next(row for row in page["bizData"] if row["outcome"] == "ADMITTED")
    instance_id = fire["instanceId"]
    wait_status(client, instance_id, {"FINISHED"})
    instance = client.call("GET", f"/api/v1/task-instances/{instance_id}")
    assert instance["triggerType"] == "SCHEDULE" and instance["scheduleId"] == identity
    assert instance["scheduleRevision"] == 2 and instance["scheduledTime"] == fire["scheduledTime"]
    assert instance["definitionId"] == definition and instance["definitionVersion"] == 1
    assert query_postgres("SELECT COUNT(*) FROM public.scheduled_orders") == "2"
    assert claim_count(instance_id) == 0
    logs = client.download(f"/api/v1/task-instances/{instance_id}/logs/download").decode("utf-8")
    assert "JDBC query executed" in logs and "JDBC sink summary" in logs and DB_PASSWORD not in logs
    assert query_mysql("SELECT COUNT(*) FROM yak_ops.YAK_QRTZ_JOB_DETAILS WHERE JOB_NAME='"
                       + client.workspace + "/" + identity + "'") == "0"
    # A second actual Cron boundary after stop must not produce another admitted instance.
    deadline = time.monotonic() + 11
    while time.monotonic() < deadline:
        assert query_mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_instance WHERE schedule_id='" + identity + "'") == "1"
        time.sleep(0.5)
    print("PASS scheduled JDBC: Quartz -> fire admission -> Instance -> dispatcher -> JDBC", flush=True)


def verify_busy_schedule(client, source, target):
    """A continuous writer owns the same target; the new periodic fire must be a durable skip."""
    definition = create_task(client, "busy-schedule-" + uuid.uuid4().hex[:8], source, target, False)
    schedule = client.call("POST", "/api/v1/schedules", {
        "targetType": "TASK", "targetId": definition, "cronExpression": "0/5 * * * * ?", "timeZone": "UTC"})
    identity = schedule["id"]
    client.call("POST", f"/api/v1/schedules/{identity}/enable", {"revision": 1})
    page = until("Resource-busy schedule fire", lambda: fire_page(client, identity),
                 lambda value: value["pagination"]["total"] > 0, timeout=45)
    client.call("POST", f"/api/v1/schedules/{identity}/disable", {"revision": 2})
    assert all(row["outcome"] == "SKIPPED" and row["reason"] == "RESOURCE_BUSY"
               and row.get("instanceId") is None for row in page["bizData"])
    assert query_mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_instance WHERE schedule_id='" + identity + "'") == "0"
    print("PASS scheduled resource conflict: durable skip without an Instance", flush=True)


def acceptance():
    STATE_ROOT.mkdir(parents=True, exist_ok=True)
    query_mysql(
        "CREATE DATABASE IF NOT EXISTS app;"
        "CREATE TABLE IF NOT EXISTS app.orders"
        " (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL);"
        "INSERT INTO app.orders VALUES (1,'one'),(2,'two');")
    query_postgres(
        "CREATE TABLE IF NOT EXISTS public.ods_orders "
        "(id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    app = boot()
    try:
        verify_single_embedded_owner()
        client = Client()
        client.login()
        workspace = client.call(
            "POST", "/api/v1/workspaces",
            {"name": "acceptance-" + uuid.uuid4().hex[:10]})["id"]
        client.workspace = workspace
        source = create_datasource(
            client, "mysql-" + uuid.uuid4().hex[:8],
            "MYSQL", "app", "root", 3306)
        target = create_datasource(
            client, "postgres-" + uuid.uuid4().hex[:8],
            "POSTGRE_SQL", "ods", "yak", 5432)

        choices = client.call("GET", "/api/v1/data-sync/solutions?sourceType=MYSQL&targetType=POSTGRE_SQL")
        assert [choice["solutionType"] for choice in choices] == ["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE", "DATABASE_REALTIME"]
        assert choices[1]["defaultSteps"] == ["FULL", "INCREMENTAL"]
        assert len(client.call("GET", "/api/v1/data-sync/solutions?sourceType=POSTGRE_SQL&targetType=MYSQL")) == 2
        client.call("GET", "/api/v1/data-sync/solutions?sourceType=JDBC&targetType=MYSQL", status=400)
        jdbc = create_task(client, "jdbc-" + uuid.uuid4().hex[:8],
                           source, target, False, debug=True)
        schedule_id = verify_schedule_configuration(client, jdbc)
        accepted, key = submit(client, jdbc)
        first = accepted["instanceId"]
        assert not accepted["replayed"]
        replay, _ = submit(client, jdbc, key)
        assert replay["instanceId"] == first and replay["replayed"]
        wait_status(client, first, {"FINISHED"})
        assert query_postgres(
            "SELECT COUNT(*) FROM public.ods_orders") == "2"
        assert claim_count(first) == 0

        # Dashboard metrics read the pinned version rather than scanning Instance pages.
        dashboard_path = "/api/v1/data-sync/operations/dashboard"
        offline_metrics = client.call("GET", dashboard_path + "?syncType=OFFLINE&range=LAST_7_DAYS")
        assert offline_metrics["summary"]["executionCount"] == 1
        assert offline_metrics["summary"]["succeededCount"] == 1
        assert offline_metrics["summary"]["failedCount"] == 0
        assert offline_metrics["summary"]["runningCount"] == 0
        assert len(offline_metrics["trend"]) == 7
        assert sum(point["executionCount"] for point in offline_metrics["trend"]) == 1
        assert offline_metrics["recentFailures"] == []
        assert client.call(
            "GET", dashboard_path + "?syncType=REALTIME&range=LAST_7_DAYS"
        )["summary"]["executionCount"] == 0
        assert client.call("GET", dashboard_path + "?syncType=OTHER", status=400) is None
        assert client.call(
            "GET", dashboard_path + "?syncType=OFFLINE&range=FOREVER", status=400
        ) is None

        # Another Workspace cannot observe this Task's metrics.
        second_workspace = client.call(
            "POST", "/api/v1/workspaces",
            {"name": "metrics-" + uuid.uuid4().hex[:10]})["id"]
        client.workspace = second_workspace
        empty_metrics = client.call(
            "GET", dashboard_path + "?syncType=OFFLINE&range=LAST_7_DAYS")
        assert empty_metrics["summary"]["executionCount"] == 0
        assert empty_metrics["summary"]["runningCount"] == 0
        client.workspace = workspace

        # Public operations must reflect the admitted Definition version and lifecycle.
        instance = client.call("GET", f"/api/v1/task-instances/{first}")
        assert instance["definitionId"] == jdbc
        assert instance["triggerType"] == "MANUAL"
        assert instance["status"] == "FINISHED"
        assert instance["runtimeEngine"] == "YAKFLOW"
        assert instance["debug"] is True
        assert instance["runtimeId"]
        assert instance["progressPercent"] == 100
        latest = client.call("POST", "/api/v1/task-instances/latest", {
            "definitionIds": [jdbc]})
        assert len(latest) == 1 and latest[0]["id"] == first
        assert latest[0]["progressPercent"] == 100
        assert client.call("POST", "/api/v1/task-instances/latest", {
            "definitionIds": [jdbc], "activeOnly": True}) == []
        log_page = until(
            "JDBC task log",
            lambda: client.call("GET", f"/api/v1/task-instances/{first}/logs?limit=200"),
            lambda page: "YakFlow Job started" in page["content"], timeout=35)
        assert log_page["nextCursor"]
        jdbc_log = client.download(f"/api/v1/task-instances/{first}/logs/download").decode("utf-8")
        for event in ("YakFlow Job started", "JDBC query executed", "JDBC fetch completed",
                      "JDBC statement batch executed", "JDBC flush completed",
                      "Source summary", "JDBC sink summary"):
            assert event in jdbc_log, f"Missing JDBC diagnostic event: {event}"
        assert DB_PASSWORD not in jdbc_log
        assert client.call("GET", f"/api/v1/task-instances/{first}/logs?cursor=bad!!", status=400) is None
        snapshot = client.call("GET", f"/api/v1/task-instances/{first}/definition")
        assert snapshot["definitionId"] == jdbc
        assert snapshot["version"] == accepted["definitionVersion"]
        assert snapshot["taskType"] == "DATA_SYNC"
        assert snapshot["parameterSchemaVersion"] == 3
        assert json.loads(snapshot["parameters"])["solutionType"] == "SINGLE_TABLE_OFFLINE"

        filtered = client.call("POST", "/api/v1/task-instances/page", {
            "pageNo": 1, "pageSize": 20, "definitionId": jdbc,
            "taskType": "DATA_SYNC", "status": "FINISHED"})
        assert filtered["pagination"]["total"] == 1
        assert len(filtered["bizData"]) == 1
        assert filtered["bizData"][0]["id"] == first
        excluded = client.call("POST", "/api/v1/task-instances/page", {
            "pageNo": 1, "pageSize": 20, "definitionId": jdbc,
            "status": "RUNNING"})
        assert excluded["pagination"]["total"] == 0

        from multitable_batch import verify_multitable_batch, verify_multitable_conflict
        batch_evidence = verify_multitable_batch(globals(), client, source, target)
        verify_parameter_versions(client, source, target, jdbc)
        verify_periodic_jdbc(client, source, target)
        if os.environ.get("E2E_BROWSER") == "true":
            from task_schedule_browser import verify_schedule_browser
            query_postgres("CREATE TABLE public.browser_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
            browser_task = create_task(client, "browser-" + uuid.uuid4().hex[:8], source, target, False,
                                       debug=True, sink_table="browser_orders")
            verify_schedule_browser(client, browser_task)
            assert query_postgres("SELECT COUNT(*) FROM public.browser_orders") == "2"


        # A soft-deleted Definition cannot remove a historical Instance's pinned version.
        # Use a future yearly expression, then activate via the real authenticated API.
        client.call("PUT", f"/api/v1/schedules/{schedule_id}", {
            "revision": 1, "cronExpression": "0 0 0 1 1 ?", "timeZone": "UTC"})
        client.call("POST", f"/api/v1/schedules/{schedule_id}/enable", {"revision": 2})
        client.call("POST", f"/api/v1/task-definitions/{jdbc}/unpublish")
        stopped = client.call("GET", f"/api/v1/schedules/{schedule_id}")
        assert stopped["enabled"] is False and stopped["revision"] == 4
        assert stopped["eligibilityReason"] == "TARGET_UNPUBLISHED"
        client.call("DELETE", f"/api/v1/task-definitions/{jdbc}")
        assert client.call("GET", f"/api/v1/task-instances/{first}/definition") == snapshot
        assert client.call("GET", f"/api/v1/schedules/{schedule_id}")["eligibilityReason"] == "TARGET_DELETED"
        client.call("DELETE", f"/api/v1/schedules/{schedule_id}?revision=4")

        cdc = create_task(client, "cdc-" + uuid.uuid4().hex[:8],
                          source, target, True)
        cdc_schedule = client.call("POST", "/api/v1/schedules", {
            "targetType": "TASK", "targetId": cdc, "cronExpression": "0 * * * * ?", "timeZone": "UTC"})
        assert not cdc_schedule["schedulable"] and cdc_schedule["eligibilityReason"] == "NOT_SCHEDULABLE"
        client.call("POST", f"/api/v1/schedules/{cdc_schedule['id']}/enable", {"revision": 1}, status=400)
        continuous, _ = submit(client, cdc)
        running = continuous["instanceId"]
        wait_status(client, running, {"RUNNING"})
        assert claim_count(running) == 2
        verify_multitable_conflict(globals(), client, source, target)
        verify_busy_schedule(client, source, target)
        assert client.call("GET", f"/api/v1/task-instances/{running}")["debug"] is False
        live_metrics = client.call(
            "GET", dashboard_path + "?syncType=REALTIME&range=LAST_7_DAYS")
        assert live_metrics["summary"]["executionCount"] == 1
        assert live_metrics["summary"]["runningCount"] == 1
        until(
            "CDC task log",
            lambda: client.call("GET", f"/api/v1/task-instances/{running}/logs?limit=200"),
            lambda page: "YakFlow Job started" in page["content"], timeout=35)
        claim_types = query_mysql(
            "SELECT GROUP_CONCAT(resource_type ORDER BY resource_type) "
            + "FROM yak_ops.yak_ops_task_execution_claim "
            + f"WHERE instance_id = '{running}'")
        assert claim_types == "DATA_SYNC_TARGET,MYSQL_CDC_SERVER_ID"
        conflict, _ = submit(client, cdc, expected=409)
        assert conflict is None
        query_mysql("INSERT INTO app.orders VALUES (3,'three')")
        until(
            "CDC replicated MySQL change",
            lambda: query_postgres(
                "SELECT COUNT(*) FROM public.ods_orders"),
            lambda count: count == "3", timeout=160)
        assert (STATE_ROOT / first).resolve() != (STATE_ROOT / running).resolve()
        checkpoint = STATE_ROOT / running / "checkpoint.bin"
        until(
            "CDC durable checkpoint",
            lambda: checkpoint.stat().st_size if checkpoint.is_file() else 0,
            lambda size: size > 32,
            timeout=100)
        client.call(
            "POST", f"/api/v1/task-instances/{running}/cancel")
        wait_status(client, running, {"CANCELED"}, timeout=35)
        assert claim_count(running) == 0
        stopped_metrics = client.call(
            "GET", dashboard_path + "?syncType=REALTIME&range=LAST_7_DAYS")
        assert stopped_metrics["summary"]["canceledCount"] == 1
        assert stopped_metrics["summary"]["runningCount"] == 0

        from database_realtime import verify_database_realtime
        realtime_evidence = verify_database_realtime(globals(), client, source, target)

        interrupted, _ = submit(client, cdc)
        interrupted_id = interrupted["instanceId"]
        wait_status(client, interrupted_id, {"RUNNING"})
        assert claim_count(interrupted_id) == 2
        shutdown(app, force=True)
        app = boot()
        client = Client()
        client.login()
        client.workspace = workspace
        wait_status(client, interrupted_id, {"RECONCILING"}, timeout=70)
        assert claim_count(interrupted_id) == 2
        conflict, _ = submit(client, cdc, expected=409)
        assert conflict is None
        uncertain = client.call("GET", f"/api/v1/task-instances/{interrupted_id}")
        assert uncertain["runtimeId"]
        assert uncertain["failureCode"] == "EXECUTION_OWNER_UNCERTAIN"
        assert "YakFlow Job started" in client.call(
            "GET", f"/api/v1/task-instances/{interrupted_id}/logs?limit=200")["content"]
        assert client.call("GET", f"/api/v1/task-instances/{batch_evidence[0]}")["executionReport"] == batch_evidence[1]
        for identity, report in realtime_evidence:
            assert client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"] == report
        print(
            "PRODUCT E2E PASS: JDBC, CDC, idempotency, resource exclusion, "
            "cancellation and restart reconciliation")
    finally:
        shutdown(app)


if __name__ == "__main__":
    acceptance()