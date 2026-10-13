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


def create_task(client, name, source, target, streaming):
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
        "name": name, "taskType": "DATA_SYNC",
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
                           source, target, False)
        accepted, key = submit(client, jdbc)
        first = accepted["instanceId"]
        assert not accepted["replayed"]
        replay, _ = submit(client, jdbc, key)
        assert replay["instanceId"] == first and replay["replayed"]
        wait_status(client, first, {"FINISHED"})
        assert query_postgres(
            "SELECT COUNT(*) FROM public.ods_orders") == "2"
        assert claim_count(first) == 0
        attempts = client.call(
            "GET", f"/api/v1/task-instances/{first}/attempts")
        assert len(attempts) == 1 and attempts[0]["status"] == "FINISHED"

        cdc = create_task(client, "cdc-" + uuid.uuid4().hex[:8],
                          source, target, True)
        continuous, _ = submit(client, cdc)
        running = continuous["instanceId"]
        wait_status(client, running, {"RUNNING"})
        assert claim_count(running) == 1
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

        interrupted, _ = submit(client, cdc)
        interrupted_id = interrupted["instanceId"]
        wait_status(client, interrupted_id, {"RUNNING"})
        assert claim_count(interrupted_id) == 1
        shutdown(app, force=True)
        app = boot()
        client = Client()
        client.login()
        client.workspace = workspace
        wait_status(client, interrupted_id, {"RECONCILING"}, timeout=70)
        assert claim_count(interrupted_id) == 1
        conflict, _ = submit(client, cdc, expected=409)
        assert conflict is None
        old_attempt = client.call(
            "GET", f"/api/v1/task-instances/{interrupted_id}/attempts")
        assert len(old_attempt) == 1
        print(
            "PRODUCT E2E PASS: JDBC, CDC, idempotency, resource exclusion, "
            "cancellation and restart reconciliation")
    finally:
        shutdown(app)


if __name__ == "__main__":
    acceptance()
