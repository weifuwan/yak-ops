"""Explicit batch acceptance through public APIs and disposable real databases."""
import json
import os
import uuid


def params(source, target, routes):
    return {"solutionType": "DATABASE_OFFLINE", "steps": ["FULL"],
            "source": {"datasourceId": source}, "sink": {"datasourceId": target},
            "tableSelection": {"mode": "EXPLICIT"}, "tableMappings": routes}


def route(source, target, write="UPSERT"):
    return {"source": {"table": source}, "target": {"schema": "public", "table": target}, "writeMode": write}


def create(client, parameters, publish=True):
    task = client.call("POST", "/api/v1/task-definitions", {
        "name": "batch-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC", "debug": True,
        "parameters": json.dumps(parameters)})
    if publish:
        client.call("POST", f"/api/v1/task-definitions/{task['id']}/publish")
    return task["id"]


def verify_multitable_batch(api, client, source, target):
    mysql, pg = api["query_mysql"], api["query_postgres"]
    submit, wait, count, until = api["submit"], api["wait_status"], api["claim_count"], api["until"]
    for table in ("batch_a", "batch_b", "batch_empty", "batch_slow"):
        mysql(f"CREATE TABLE app.{table} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    mysql("INSERT INTO app.batch_a VALUES (1,'a'),(2,'b')")
    mysql("INSERT INTO app.batch_b VALUES (11,'c'),(12,'d'),(13,'e')")
    mysql("INSERT INTO app.batch_slow VALUES " + ",".join(f"({i},'value')" for i in range(1, 1001)))
    for table in ("batch_a_copy", "batch_b_copy", "batch_empty_copy", "batch_empty_second", "batch_slow_copy", "batch_good_copy", "aaa_orders"):
        pg(f"CREATE TABLE public.{table} (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)")
    pg("CREATE TABLE public.batch_bad_copy (id BIGINT PRIMARY KEY CHECK (id < 0), name VARCHAR(32) NOT NULL)")
    routes = [route("batch_a", "batch_a_copy", "APPEND"), route("batch_b", "batch_b_copy"), route("batch_empty", "batch_empty_copy")]
    data = params(source, target, routes)
    task = create(client, data)
    accepted, _ = submit(client, task)
    identity = accepted["instanceId"]
    wait(client, identity, {"FINISHED"})
    result = client.call("GET", f"/api/v1/task-instances/{identity}")
    assert result["runtimeId"] and result["progressPercent"] == 100
    report = json.loads(result["executionReport"])
    assert report["version"] == 1 and report["observedAt"]
    assert len(report["tables"]) == 3
    assert [row["mappingIndex"] for row in report["tables"]] == [0, 1, 2]
    assert [row["readRows"] for row in report["tables"]] == [2, 3, 0]
    assert [row["committedRows"] for row in report["tables"]] == [2, 3, 0]
    assert all(row["status"] == "FINISHED" and row["progressPercent"] == 100 for row in report["tables"])
    assert count(identity) == 0
    assert pg("SELECT string_agg(name,',' ORDER BY id) FROM public.batch_a_copy") == "a,b"
    assert pg("SELECT string_agg(name,',' ORDER BY id) FROM public.batch_b_copy") == "c,d,e"
    assert pg("SELECT COUNT(*) FROM public.batch_empty_copy") == "0"
    assert api["DB_PASSWORD"] not in result["executionReport"]
    assert client.call("POST", "/api/v1/task-instances/page", {"definitionId": task, "pageNo": 1, "pageSize": 20})["pagination"]["total"] == 1
    page = client.call("POST", "/api/v1/task-definitions/page", {"taskType": "DATA_SYNC", "parameterFilters": {"solutionType": "DATABASE_OFFLINE"}, "pageNo": 1, "pageSize": 100})
    assert task in {value["id"] for value in page["bizData"]}
    snapshot = client.call("GET", f"/api/v1/task-instances/{identity}/definition")
    client.call("POST", f"/api/v1/task-definitions/{task}/unpublish")
    changed = params(source, target, [route("batch_a", "batch_a_copy"), route("batch_b", "batch_b_copy"), route("batch_empty", "batch_empty_second")])
    updated = client.call("PUT", f"/api/v1/task-definitions/{task}", {"name": snapshot["name"], "parameters": json.dumps(changed)})
    assert updated["definitionVersion"] == 2
    assert client.call("GET", f"/api/v1/task-instances/{identity}/definition") == snapshot
    assert client.call("GET", f"/api/v1/task-instances/{identity}")["executionReport"] == result["executionReport"]
    # Another Workspace may not read reports or reference these actual connections.
    original = client.workspace
    client.workspace = client.call("POST", "/api/v1/workspaces", {"name": "batch-other-" + uuid.uuid4().hex[:8]})["id"]
    try:
        client.call("GET", f"/api/v1/task-instances/{identity}", status=404)
    finally:
        client.workspace = original
    # All schemas must validate before any admission or write occurs.
    pg("CREATE TABLE public.batch_wrong_schema (id BIGINT PRIMARY KEY, name INTEGER NOT NULL)")
    bad_schema = create(client, params(source, target, [route("batch_a", "batch_a_copy"), route("batch_b", "batch_wrong_schema")]), publish=False)
    client.call("POST", f"/api/v1/task-definitions/{bad_schema}/publish", status=400)
    assert client.call("POST", "/api/v1/task-instances/page", {"definitionId": bad_schema, "pageNo": 1, "pageSize": 20})["pagination"]["total"] == 0
    for bad_routes in ([], [routes[0], routes[0]], [routes[0], route("batch_b", "batch_a_copy")]):
        client.call("POST", "/api/v1/task-definitions", {"name": "bad-batch-" + uuid.uuid4().hex[:8], "taskType": "DATA_SYNC", "parameters": json.dumps(params(source, target, bad_routes))}, status=400)
    # The ordinary batch transaction size creates acknowledged writes before a later table fails.
    failing = create(client, params(source, target, [route("batch_slow", "batch_good_copy"), route("batch_b", "batch_bad_copy")]))
    failure, _ = submit(client, failing)
    wait(client, failure["instanceId"], {"FAILED"})
    rows = json.loads(client.call("GET", f"/api/v1/task-instances/{failure['instanceId']}")["executionReport"])["tables"]
    assert rows[0]["status"] == "FINISHED" and rows[0]["committedRows"] == 1000
    assert rows[1]["status"] == "FAILED" and rows[1]["failureCode"] == "WRITE_FAILED"
    assert rows[1]["committedRows"] == 0 and pg("SELECT COUNT(*) FROM public.batch_bad_copy") == "0"
    assert pg("SELECT COUNT(*) FROM public.batch_good_copy") == "1000"
    assert count(failure["instanceId"]) == 0
    # Cancellation must release every destination, not only the currently active write route.
    pg("CREATE FUNCTION batch_delay() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.02); RETURN NEW; END $$")
    pg("CREATE TRIGGER batch_delay BEFORE INSERT ON public.batch_slow_copy FOR EACH ROW EXECUTE FUNCTION batch_delay()")
    slow = create(client, params(source, target, [route("batch_slow", "batch_slow_copy"), route("batch_empty", "batch_empty_second")]))
    running, _ = submit(client, slow)
    wait(client, running["instanceId"], {"RUNNING"})
    assert count(running["instanceId"]) == 2
    def progressed():
        observation = client.call("GET", f"/api/v1/task-instances/{running['instanceId']}")
        report = observation.get("executionReport")
        return report and json.loads(report)["tables"][0]["readRows"] > 0
    until("Batch source emitted records", progressed, bool, timeout=30)
    client.call("POST", f"/api/v1/task-instances/{running['instanceId']}/cancel")
    wait(client, running["instanceId"], {"CANCELED"}, timeout=45)
    assert count(running["instanceId"]) == 0
    canceled_report = json.loads(client.call("GET", f"/api/v1/task-instances/{running['instanceId']}")["executionReport"])
    assert any(row["status"] == "CANCELED" for row in canceled_report["tables"])
    pg("DROP TRIGGER batch_delay ON public.batch_slow_copy")
    # One explicit mapping still keeps DATABASE_OFFLINE and is eligible for real Quartz admission.
    scheduled = create(client, params(source, target, [route("batch_a", "batch_a_copy")]))
    schedule = client.call("POST", "/api/v1/schedules", {"targetType": "TASK", "targetId": scheduled, "cronExpression": "0/10 * * * * ?", "timeZone": "UTC"})
    client.call("POST", f"/api/v1/schedules/{schedule['id']}/enable", {"revision": 1})
    fires = until("Scheduled batch admission", lambda: api["fire_page"](client, schedule["id"]), lambda page: any(row["outcome"] == "ADMITTED" for row in page["bizData"]), timeout=60)
    client.call("POST", f"/api/v1/schedules/{schedule['id']}/disable", {"revision": 2})
    admitted = next(row for row in fires["bizData"] if row["outcome"] == "ADMITTED")
    wait(client, admitted["instanceId"], {"FINISHED"})
    assert json.loads(client.call("GET", f"/api/v1/task-instances/{admitted['instanceId']}/definition")["parameters"])["solutionType"] == "DATABASE_OFFLINE"
    assert len(json.loads(client.call("GET", f"/api/v1/task-instances/{admitted['instanceId']}")["executionReport"])["tables"]) == 1
    if os.environ.get("E2E_BROWSER") == "true":
        from multitable_browser import verify_multitable_browser
        verify_multitable_browser(api, client, source, target)
    print("MULTITABLE PRODUCT PASS: one Job, mixed routes, empty table, pinned report, schema guards, failure, cancellation and Quartz", flush=True)
    return identity, result["executionReport"]


def verify_multitable_conflict(api, client, source, target):
    data = params(source, target, [route("batch_a", "aaa_orders"), route("batch_b", "ods_orders")])
    task = create(client, data)
    before = api["query_mysql"]("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim")
    api["submit"](client, task, expected=409)
    assert api["query_mysql"]("SELECT COUNT(*) FROM yak_ops.yak_ops_task_execution_claim") == before
    assert client.call("POST", "/api/v1/task-instances/page", {"definitionId": task, "pageNo": 1, "pageSize": 20})["pagination"]["total"] == 0
    assert api["query_postgres"]("SELECT COUNT(*) FROM public.aaa_orders") == "0"
    print("MULTITABLE CONFLICT PASS: occupied second destination rolls back the full claim set and creates no Instance", flush=True)
