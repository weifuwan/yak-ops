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
            "POST", "/yak-security/api/v1/account/login",
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
            "jdbc:mysql://127.0.0.1:3306/yak_security"
            "?useSSL=false&allowPublicKeyRetrieval=true"),
        "YAK_DATABASE_USERNAME": "root",
        "YAK_DATABASE_PASSWORD": DB_PASSWORD,
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


def create_task(client, name, source, target, streaming, debug=False):
    params = {
        "source": {
            "type": "MYSQL_CDC" if streaming else "JDBC",
            "datasourceId": source, "table": "orders",
        },
        "transform": [],
        "sink": {
            "type": "JDBC", "datasourceId": target,
            "schema": "public", "table": "ods_orders",
            "writeMode": "UPSERT" if streaming else "APPEND",
        },
    }
    definition = client.call("POST", "/api/v1/task-definitions", {
        "name": name, "taskType": "DATA_SYNC", "debug": debug,
        "parameters": json.dumps(params, separators=(",", ":")),
    })["id"]
    client.call("POST", f"/api/v1/task-definitions/{definition}/publish")
    return definition


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
        "SELECT COUNT(*) FROM yak_security.yak_ops_task_execution_claim "
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
    assert created["registrationStatus"] == "PENDING"
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
        client.call("GET", base + "/" + identity, status=404)
        client.call("DELETE", base + "/" + identity + "?revision=2", status=404)
        client.call("POST", base, payload, status=404)
    finally:
        client.workspace = workspace
    client.call("DELETE", base + "/" + identity + "?revision=2")
    restored = client.call("POST", base, payload)
    assert restored["id"] != identity and restored["enabled"] is False
    assert query_mysql("SELECT COUNT(*) FROM yak_security.yak_ops_schedule WHERE id='" + identity + "' AND deleted_at IS NOT NULL") == "1"
    # The database, not just a pre-insert service query, owns target uniqueness.
    duplicate_sql = ("INSERT INTO yak_security.yak_ops_schedule (id,workspace_id,target_type,target_id,cron_expression,time_zone,"
                     "create_time,update_time,create_by,update_by) SELECT 'duplicate-schedule',workspace_id,target_type,target_id,"
                     "cron_expression,time_zone,create_time,update_time,create_by,update_by FROM yak_security.yak_ops_schedule WHERE id='" + restored["id"] + "'")
    try:
        query_mysql(duplicate_sql)
    except subprocess.CalledProcessError:
        pass
    else:
        raise AssertionError("MySQL allowed duplicate live schedule target")
    return restored["id"]


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
        assert json.loads(snapshot["parameters"])["source"]["type"] == "JDBC"

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

        # A soft-deleted Definition cannot remove a historical Instance's pinned version.
        query_mysql("UPDATE yak_security.yak_ops_schedule SET enabled=1,enabled_at=UTC_TIMESTAMP(3),enabled_by=create_by WHERE id='" + schedule_id + "'")
        client.call("POST", f"/api/v1/task-definitions/{jdbc}/unpublish")
        stopped = client.call("GET", f"/api/v1/schedules/{schedule_id}")
        assert stopped["enabled"] is False and stopped["revision"] == 2
        assert stopped["eligibilityReason"] == "TARGET_UNPUBLISHED"
        client.call("DELETE", f"/api/v1/task-definitions/{jdbc}")
        assert client.call("GET", f"/api/v1/task-instances/{first}/definition") == snapshot
        assert client.call("GET", f"/api/v1/schedules/{schedule_id}")["eligibilityReason"] == "TARGET_DELETED"
        client.call("DELETE", f"/api/v1/schedules/{schedule_id}?revision=2")

        cdc = create_task(client, "cdc-" + uuid.uuid4().hex[:8],
                          source, target, True)
        cdc_schedule = client.call("POST", "/api/v1/schedules", {
            "targetType": "TASK", "targetId": cdc, "cronExpression": "0 * * * * ?", "timeZone": "UTC"})
        assert not cdc_schedule["schedulable"] and cdc_schedule["eligibilityReason"] == "NOT_SCHEDULABLE"
        continuous, _ = submit(client, cdc)
        running = continuous["instanceId"]
        wait_status(client, running, {"RUNNING"})
        assert claim_count(running) == 2
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
            + "FROM yak_security.yak_ops_task_execution_claim "
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
        print(
            "PRODUCT E2E PASS: JDBC, CDC, idempotency, resource exclusion, "
            "cancellation and restart reconciliation")
    finally:
        shutdown(app)


if __name__ == "__main__":
    acceptance()
