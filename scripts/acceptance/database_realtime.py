"""Database realtime acceptance through authenticated APIs and disposable live databases."""
import json
import os
import time
import uuid


def parameters(source, target, routes):
    return {"solutionType": "DATABASE_REALTIME", "steps": ["FULL", "INCREMENTAL"],
            "source": {"datasourceId": source}, "sink": {"datasourceId": target},
            "tableSelection": {"mode": "EXPLICIT"}, "tableMappings": routes}


def create(client, data, publish=True):
    task = client.call("POST", "/api/v1/task-definitions", {
        "name": "realtime-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC",
        "parameters": json.dumps(data)})["id"]
    if publish:
        client.call("POST", f"/api/v1/task-definitions/{task}/publish")
    return task


def report(client, identity):
    instance = client.call("GET", f"/api/v1/task-instances/{identity}")
    assert instance["status"] not in ("FAILED", "CANCELED", "RECONCILING"), instance
    assert instance.get("progressPercent") is None, instance
    return json.loads(instance["executionReport"]) if instance.get("executionReport") else None


def verify_database_realtime(api, client, source, postgres):
    mysql, pg = api["query_mysql"], api["query_postgres"]
    submit, wait, until, claims = api["submit"], api["wait_status"], api["until"], api["claim_count"]
    mysql("CREATE DATABASE rt_target")
    target_mysql = api["create_datasource"](client, "rt-mysql-" + uuid.uuid4().hex[:8], "MYSQL", "rt_target", "root", 3306)
    evidence = []
    browser_routes = None
    # Oracle NUMBER keys require a separate checked conversion contract. Do not advertise it.
    oracle_choices = client.call("GET", "/api/v1/data-sync/solutions?sourceType=MYSQL&targetType=ORACLE")
    assert "DATABASE_REALTIME" not in {value["solutionType"] for value in oracle_choices}
    for kind, target, query, namespace in (("mysql", target_mysql, mysql, "rt_target"), ("pg", postgres, pg, "public")):
        prefix = "rt_" + kind
        tables = [prefix + "_a", prefix + "_b", prefix + "_empty"]
        routes = []
        for index, table in enumerate(tables):
            mysql(f"CREATE TABLE app.{table} (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)")
            if index < 2:
                mysql(f"INSERT INTO app.{table} VALUES " + ",".join(f"({i},'initial')" for i in range(1, 201)))
            query(f"CREATE TABLE {namespace}.{table}_copy (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)")
            path = {"database": namespace, "table": table + "_copy"} if kind == "mysql" else {"schema": namespace, "table": table + "_copy"}
            routes.append({"source": {"database": "app", "table": table}, "target": path, "writeMode": "UPSERT"})
        data = parameters(source, target, routes)
        task = create(client, data)
        schedule = client.call("POST", "/api/v1/schedules", {
            "targetType": "TASK", "targetId": task, "cronExpression": "0 * * * * ?", "timeZone": "UTC"})
        assert not schedule["schedulable"] and schedule["eligibilityReason"] == "NOT_SCHEDULABLE"
        client.call("POST", f"/api/v1/schedules/{schedule['id']}/enable", {"revision": 1}, status=400)
        accepted, _ = submit(client, task)
        identity = accepted["instanceId"]
        wait(client, identity, {"RUNNING"})
        assert claims(identity) == 4
        claim_types = mysql("SELECT GROUP_CONCAT(resource_type ORDER BY resource_type) FROM yak_ops.yak_ops_task_execution_claim "
                            + f"WHERE instance_id='{identity}'")
        assert claim_types == "DATA_SYNC_TARGET,DATA_SYNC_TARGET,DATA_SYNC_TARGET,MYSQL_CDC_SERVER_ID"
        initial = until("Realtime snapshot anchored", lambda: report(client, identity),
                        lambda value: value and value["phase"] in ("SNAPSHOT", "HANDOFF"), timeout=30)
        assert initial["stages"][0]["status"] == "RUNNING" and initial["stages"][0]["progressPercent"] != 100
        assert initial["stages"][1]["status"] == "WAITING" and initial["stages"][1]["progressPercent"] is None
        # Mutate both nonempty tables and the empty table after the actual low-watermark anchor.
        for table in tables[:2]:
            mysql(f"UPDATE app.{table} SET name='updated' WHERE id=1; DELETE FROM app.{table} WHERE id=2; "
                  f"UPDATE app.{table} SET id=300, name='moved' WHERE id=3; INSERT INTO app.{table} VALUES (400,'inserted')")
        mysql(f"INSERT INTO app.{tables[2]} VALUES (1,'from-binlog')")
        streaming = until("Realtime committed handoff", lambda: report(client, identity),
                          lambda value: value and value["phase"] == "STREAMING", timeout=140)
        assert streaming["version"] == 2 and streaming["generation"] == 1
        assert streaming["stages"][0]["status"] == "FINISHED" and streaming["stages"][0]["progressPercent"] == 100
        assert streaming["checkpoints"]["handoffId"] is not None
        assert streaming["stages"][1]["status"] == "RUNNING" and streaming["stages"][1]["progressPercent"] is None
        assert all(row["stages"][0]["status"] == "FINISHED" for row in streaming["tables"])
        for table in tables[:2]:
            def converged(table=table):
                return (query(f"SELECT COUNT(*) FROM {namespace}.{table}_copy") == "200"
                        and query(f"SELECT COUNT(*) FROM {namespace}.{table}_copy WHERE (id=1 AND name='updated') OR (id=300 AND name='moved') OR (id=400 AND name='inserted')") == "3"
                        and query(f"SELECT COUNT(*) FROM {namespace}.{table}_copy WHERE id IN (2,3)") == "0")
            until("Concurrent Snapshot/Binlog values converge " + table, converged, bool, timeout=90)
        until("Empty table begins incremental delivery", lambda: query(f"SELECT name FROM {namespace}.{tables[2]}_copy WHERE id=1"), lambda value: value == "from-binlog")
        # Existing list endpoints return bounded summaries, not 100 expanded table reports.
        page = client.call("POST", "/api/v1/task-instances/page", {"definitionId": task, "pageNo": 1, "pageSize": 20})
        summary = json.loads(page["bizData"][0]["executionReport"])
        assert summary["version"] == 2 and "tables" not in summary and len(page["bizData"][0]["executionReport"]) < 16384
        filtered = client.call("POST", "/api/v1/task-definitions/page", {"taskType": "DATA_SYNC", "parameterFilters": {"solutionType": "DATABASE_REALTIME"}, "pageNo": 1, "pageSize": 100})
        assert task in {entry["id"] for entry in filtered["bizData"]}
        conflict = create(client, data)
        count_before = mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim")
        submit(client, conflict, expected=409)
        assert mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim") == count_before
        assert client.call("POST", "/api/v1/task-instances/page", {"definitionId": conflict, "pageNo": 1, "pageSize": 20})["pagination"]["total"] == 0
        before_idle = report(client, identity)["observedAt"]
        until("Idle CDC remains freshly observed", lambda: report(client, identity), lambda value: value and value["observedAt"] != before_idle, timeout=15)
        if kind == "pg":
            # PostgreSQL sequences survive rollback, so exactly the first retry-row write fails.
            # A completed streaming checkpoint makes this a real same-Job recovery exercise.
            checkpoint_id = report(client, identity)["checkpoints"]["completedId"]
            until("Streaming checkpoint before injected failure", lambda: report(client, identity),
                  lambda value: value and value["checkpoints"]["completedId"] > checkpoint_id, timeout=35)
            pg("CREATE SEQUENCE rt_pg_once START 1")
            pg("CREATE FUNCTION rt_pg_retry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
               "IF NEW.name='retry' AND nextval('rt_pg_once')=1 THEN RAISE EXCEPTION 'injected acceptance write failure'; END IF; RETURN NEW; END $$")
            pg(f"CREATE TRIGGER rt_pg_retry BEFORE INSERT ON public.{tables[0]}_copy FOR EACH ROW EXECUTE FUNCTION rt_pg_retry()")
            mysql(f"INSERT INTO app.{tables[0]} VALUES (7001,'retry')")
            recovered = until("Same-Job CDC generation recovered", lambda: report(client, identity),
                              lambda value: value and value["generation"] == 2 and value["phase"] == "STREAMING", timeout=120)
            assert recovered["restored"] and recovered["counterScope"] == "GENERATION"
            assert recovered["stages"][0]["progressPercent"] == 100
            assert recovered["stages"][0]["readRows"] is None and recovered["stages"][0]["committedRows"] is None
            assert all(row["stages"][0]["readRows"] is None for row in recovered["tables"])
            until("Recovered generation commits replay", lambda: pg(f"SELECT name FROM public.{tables[0]}_copy WHERE id=7001"), lambda value: value == "retry")
            pg(f"DROP TRIGGER rt_pg_retry ON public.{tables[0]}_copy; DROP FUNCTION rt_pg_retry(); DROP SEQUENCE rt_pg_once")
            browser_routes = routes
        pinned = client.call("GET", f"/api/v1/task-instances/{identity}/definition")
        client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
        wait(client, identity, {"CANCELED"}, timeout=45)
        assert claims(identity) == 0
        terminal = client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"]
        final = json.loads(terminal)
        assert final["stages"][0]["status"] == "FINISHED" and final["stages"][1]["status"] == "CANCELED"
        assert api["DB_PASSWORD"] not in terminal
        client.call("POST", f"/api/v1/task-definitions/{task}/unpublish")
        changed = parameters(source, target, list(reversed(routes)))
        client.call("PUT", f"/api/v1/task-definitions/{task}", {"name": pinned["name"], "parameters": json.dumps(changed)})
        assert client.call("GET", f"/api/v1/task-instances/{identity}/definition") == pinned
        assert client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"] == terminal
        evidence.append((identity, terminal))
        print("DATABASE REALTIME PASS: MySQL -> " + kind + "; three tables, concurrent handoff, ordered changes, summaries, claims, cancellation and pinned history", flush=True)
    # Cancel during slow downstream Snapshot writes. Read EOF alone must not complete FULL.
    pg("CREATE TABLE public.rt_cancel (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    pg("CREATE FUNCTION rt_cancel_delay() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.04); RETURN NEW; END $$")
    pg("CREATE TRIGGER rt_cancel_delay BEFORE INSERT ON public.rt_cancel FOR EACH ROW EXECUTE FUNCTION rt_cancel_delay()")
    cancel_routes = [{"source": {"database": "app", "table": "rt_pg_a"}, "target": {"schema": "public", "table": "rt_cancel"}, "writeMode": "UPSERT"}]
    task = create(client, parameters(source, postgres, cancel_routes))
    accepted, _ = submit(client, task)
    identity = accepted["instanceId"]
    until("Slow Snapshot entered", lambda: report(client, identity), lambda value: value and value["phase"] in ("SNAPSHOT", "HANDOFF"))
    client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
    wait(client, identity, {"CANCELED"}, timeout=45)
    assert claims(identity) == 0
    result = json.loads(client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"])
    assert result["stages"][0]["status"] == "CANCELED" and result["stages"][0]["progressPercent"] != 100
    assert result["stages"][1]["status"] == "CANCELED"
    assert json.loads(client.call("GET", f"/api/v1/task-instances/{identity}/definition")["parameters"])["solutionType"] == "DATABASE_REALTIME"
    pg("DROP TRIGGER rt_cancel_delay ON public.rt_cancel; DROP FUNCTION rt_cancel_delay()")
    # Whole-collection validation blocks invalid Source keys before physical submission.
    mysql("CREATE TABLE app.rt_invalid_key (id VARCHAR(32) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    bad = parameters(source, postgres, [{"source": {"table": "rt_invalid_key"}, "target": {"schema": "public", "table": "rt_cancel"}, "writeMode": "UPSERT"}])
    invalid = create(client, bad, publish=False)
    client.call("POST", f"/api/v1/task-definitions/{invalid}/publish", status=400)
    assert client.call("POST", "/api/v1/task-instances/page", {"definitionId": invalid, "pageNo": 1, "pageSize": 20})["pagination"]["total"] == 0
    if os.environ.get("E2E_BROWSER") == "true":
        from database_realtime_browser import verify_database_realtime_browser
        verify_database_realtime_browser(api, client, source, postgres, browser_routes)
    return evidence
