"""Deterministic HTTP fixtures for UI interaction tests; never evidence for backend E2E."""

import json
from copy import deepcopy
from urllib.parse import urlparse, parse_qs

params = {
    "source": {
        "type": "jdbc-mysql",
        "datasourceId": "src",
        "syncMode": "FULL",
        "tables": [{"database": "app", "table": "orders"}],
    },
    "sink": {
        "type": "jdbc-postgres",
        "datasourceId": "dst",
        "schemaPolicy": {
            "initial": "VERIFY",
            "evolution": {"enabled": False, "allowDropColumns": False},
        },
        "tables": [{
            "database": "ods", "schema": "public",
            "table": "ods_orders", "write": {"mode": "APPEND"},
        }],
    },
    "route": [{"source-table": "app.orders", "sink-table": "ods.public.ods_orders"}],
    "transform": [],
    "pipeline": {"execution.runtime-mode": "BATCH"},
}
task = {
    "id": "task1",
    "name": "Orders sync",
    "taskType": "DATA_SYNC",
    "status": "PUBLISHED",
    "definitionVersion": 1,
    "parameterSchemaVersion": 7,
    "parameters": params,
    "debug": False,
    "updateBy": "user1",
    "createTime": "2030-10-14T00:00:00",
    "updateTime": "2030-10-14T00:00:00",
}
instance = {
    "id": "i1",
    "definitionId": "task1",
    "definitionVersion": 1,
    "taskType": "DATA_SYNC",
    "triggerType": "SCHEDULE",
    "scheduleId": "s1",
    "scheduleRevision": 2,
    "scheduledTime": "2030-10-14T00:01:00Z",
    "status": "FINISHED",
    "progressPercent": 100,
    "debug": False,
    "createTime": "2030-10-14T00:01:00",
    "startTime": "2030-10-14T00:01:00",
    "endTime": "2030-10-14T00:01:02",
}

sources = [
    {"id": "src", "name": "MySQL source", "dbType": "MYSQL", "database": "app"},
    {
        "id": "dst",
        "name": "PostgreSQL target",
        "dbType": "POSTGRE_SQL",
        "database": "ods",
        "schema": "public",
    },
]
state = {
    "schedule": None,
    "requests": [],
    "created_tasks": 0,
    "updated_tasks": 0,
    "fail_schedule": False,
    "fail_batch": False,
    "conflict": False,
    "fires": [],
    "fail_solutions": False,
    "fail_publish": False,
    "logged_out": False,
    "versions": {},
    "solution_requests": [],
    "task": task,
    "unexpected_requests": [],
    "catalog_mode": "single",
}


def paging(rows, page_no=1, page_size=200):
    total = len(rows)
    return {
        "bizData": rows[(page_no - 1) * page_size:page_no * page_size],
        "pagination": {"pageNo": page_no, "pageSize": page_size, "total": total,
                       "pages": (total + page_size - 1) // page_size},
    }


def remember_definition(definition):
    key = (definition["id"], definition["definitionVersion"])
    state["versions"].setdefault(key, deepcopy(definition))


def task_profile(definition):
    config = definition["parameters"]
    if config["source"].get("startup") or config["pipeline"]["execution.runtime-mode"] == "STREAMING":
        return "DATABASE_REPLICATION"
    return "SINGLE_TABLE_BATCH"


async def mock(route):
    r = route.request
    p = urlparse(r.url).path
    method = r.method
    b = r.post_data_json if r.post_data else {}
    state["requests"].append((method, p, b, r.headers.get("x-workspace-id")))
    status = 200
    data = None
    code = 200
    message = "test"
    if p.endswith("/account/current"):
        data = {
            "id": "user1",
            "userName": "tester",
            "realName": "Tester",
            "status": "ACTIVE",
        }
        if state["logged_out"]:
            status, code, data = 401, 401, None
    elif p.endswith("/account/logout"):
        state["logged_out"] = True
        data = True
    elif p == "/api/v1/workspaces":
        data = [
            {"id": "ws1", "name": "Workspace A", "role": "OWNER"},
            {"id": "ws2", "name": "Workspace B", "role": "OWNER"},
        ]
    elif "/user-preferences" in p:
        data = []
    elif "/users/" in p or p.endswith("/user/batch") or p == "/api/v1/user":
        data = []
    elif p == "/api/v1/data-sync/connectors":
        query = parse_qs(urlparse(r.url).query)
        state["solution_requests"].append(query)
        identifiers = {"MYSQL": "jdbc-mysql", "POSTGRE_SQL": "jdbc-postgres", "ORACLE": "jdbc-oracle"}
        source_type = query.get("sourceType", [None])[0]
        target_type = query.get("targetType", [None])[0]
        choices = []
        if source_type in identifiers and target_type in identifiers:
            capabilities = {"sinkConnector": identifiers[target_type],
                            "supportsSchemaChanges": target_type in ("MYSQL", "POSTGRE_SQL"),
                            "supportsRetract": target_type == "MYSQL"}
            choices.append({**capabilities, "sourceConnector": identifiers[source_type],
                            "producesChangelog": False, "boundedness": "BOUNDED",
                            "supportsIncrementalBatch": source_type in ("MYSQL", "POSTGRE_SQL")})
            if source_type == "MYSQL":
                choices.append({**capabilities, "sourceConnector": "mysql", "producesChangelog": True,
                                "boundedness": "CONTINUOUS_UNBOUNDED", "supportsIncrementalBatch": False})
        if state["fail_solutions"]:
            status, code = 503, 500
        else:
            data = choices
    elif p == "/api/v1/data-source/page":
        data = paging(state.get("data_sources", sources))
    elif "/catalog/schemas" in p:
        data = []
    elif "/catalog/columns" in p:
        data = state.get("source_columns" if "/src/" in p else "target_columns", [
            {"name": "id", "typeName": "BIGINT", "nullable": False, "primaryKey": True}
        ])
    elif "/catalog/tables" in p:
        mode = state["catalog_mode"]
        if "/src/" in p:
            names = state.get("source_tables", ["orders"] if mode == "single" else (
                ["sm_browser_source"] if mode == "schema" else ["batch_a", "batch_b"]))
            data = [{"database": "app", "schema": None, "name": name, "type": "TABLE"}
                    for name in names]
        else:
            names = state.get("target_tables", ["ods_orders"] if mode == "single" else (
                [] if mode == "schema" else ["batch_a_copy", "batch_b_copy"]))
            datasource = next(source for source in state.get("data_sources", sources)
                              if source["id"] == p.split("/")[4])
            data = [{"database": datasource["database"], "schema": datasource.get("schema"),
                     "name": name, "type": "TABLE"}
                    for name in names]
    elif p.startswith("/api/v1/data-source/"):
        data = next((x for x in state.get("data_sources", sources)
                     if x["id"] == p.rsplit("/", 1)[-1]), {})
    elif p == "/api/v1/task-definitions/page":
        rows = state.get("task_rows", [state["task"]])
        filters = b.get("parameterFilters", {})
        if filters.get("dataSyncProfile"):
            rows = [row for row in rows if task_profile(row) == filters["dataSyncProfile"]]
        if b.get("status"):
            rows = [row for row in rows if row["status"] == b["status"]]
        if b.get("keyword"):
            rows = [row for row in rows if b["keyword"].lower() in row["name"].lower()]
        data = paging(rows, b.get("pageNo", 1), b.get("pageSize", 20))
    elif p == "/api/v1/task-definitions" and method == "POST":
        state["created_tasks"] += 1
        state["task"] = {**task, **b, "id": "new-task", "status": "UNPUBLISHED",
                         "definitionVersion": 1, "parameterSchemaVersion": 7}
        remember_definition(state["task"])
        data = state["task"]
    elif p.startswith("/api/v1/task-definitions/"):
        if p.endswith("/publish"):
            if state["fail_publish"]:
                status, code = 400, 400
                message = "同步配置校验失败"
            else:
                state["task"] = {**state["task"], "status": "PUBLISHED"}
            data = state["task"]
        elif p.endswith("/unpublish"):
            state["task"] = {**state["task"], "status": "UNPUBLISHED"}
            data = state["task"]
        elif p.endswith("/instances"):
            remember_definition(state["task"])
            instance.update({"id": "i1", "definitionId": state["task"]["id"],
                             "definitionVersion": state["task"]["definitionVersion"],
                             "triggerType": "MANUAL", "scheduleId": None,
                             "scheduleRevision": None, "scheduledTime": None,
                             "status": "RUNNING", "progressPercent": 0,
                             "endTime": None, "executionReport": state.get("run_report")})
            state["show_instances"] = True
            data = {"instanceId": instance["id"]}
        elif method == "PUT":
            state["updated_tasks"] += 1
            remember_definition(state["task"])
            changed = "parameters" in b and b["parameters"] != state["task"]["parameters"]
            state["task"] = {**state["task"], **b,
                             "definitionVersion": state["task"]["definitionVersion"] + int(changed),
                             **({"parameterSchemaVersion": 7} if "parameters" in b else {})}
            remember_definition(state["task"])
            data = state["task"]
        else:
            remember_definition(state["task"])
            data = state["task"]
    elif p == "/api/v1/task-instances/latest":
        data = (
            [instance]
            if (not b["activeOnly"] or instance["status"] in ("SUBMITTED", "LAUNCHING", "RUNNING"))
            and instance["definitionId"] in b["definitionIds"]
            and r.headers.get("x-workspace-id") == "ws1"
            else []
        )
    elif p == "/api/v1/task-instances/page":
        data = paging([instance] if state.get("show_instances")
                      and instance["definitionId"] == b.get("definitionId")
                      and (not b.get("status") or b["status"] == instance["status"]) else [])
    elif p.startswith("/api/v1/task-instances/"):
        if "/logs" in p:
            data = {
                "content": "INFO JDBC query executed\nINFO JDBC sink summary\n",
                "nextCursor": "cursor1",
                "hasMore": False,
            }
        elif p.endswith("/definition"):
            remember_definition(state["task"])
            definition = state["versions"][(instance["definitionId"], instance["definitionVersion"])]
            data = {
                "definitionId": instance["definitionId"],
                "version": instance["definitionVersion"],
                "name": definition["name"],
                "taskType": "DATA_SYNC",
                "parameterSchemaVersion": definition["parameterSchemaVersion"],
                "parameters": definition["parameters"],
            }
        elif p.endswith("/cancel"):
            instance["status"] = "CANCELED"
            data = "CANCELED"
        elif p.endswith("/metrics"):
            data = {}
        else:
            data = instance
    elif p == "/api/v1/schedules/preview":
        if b["cronExpression"] == "invalid":
            status = 400
            code = 18001
        else:
            data = {
                "after": "2030-10-14T00:00:00Z",
                "nextFireTimes": [f"2030-10-{d:02d}T18:00:00Z" for d in range(14, 19)],
            }
    elif p == "/api/v1/schedules/page":
        if state["fail_batch"]:
            status = 503
            code = 500
        data = paging(
            [state["schedule"]]
            if state["schedule"] and r.headers.get("x-workspace-id") == "ws1"
            else []
        )
    elif p == "/api/v1/schedules" and method == "POST":
        if state["fail_schedule"]:
            status = 503
            code = 500
        else:
            state["schedule"] = {
                **b,
                "id": "s1",
                "enabled": False,
                "revision": 1,
                "registrationStatus": "APPLIED",
                "exhausted": False,
                "nextFireTime": None,
                "schedulable": True,
                "eligibilityReason": None,
            }
            data = state["schedule"]
    elif p.startswith("/api/v1/schedules/"):
        if p.endswith("/fires/page"):
            data = paging(state["fires"])
        elif p.endswith("/enable") or p.endswith("/disable"):
            state["schedule"] = {
                **state["schedule"],
                "enabled": p.endswith("/enable"),
                "revision": state["schedule"]["revision"] + 1,
            }
            data = state["schedule"]
        elif method == "PUT" and state["conflict"]:
            status = 409
            code = 18002
            state["schedule"] = {**state["schedule"], "revision": state["schedule"]["revision"] + 1}
        elif method == "PUT":
            state["schedule"] = {
                **state["schedule"],
                **b,
                "revision": state["schedule"]["revision"] + 1,
            }
            data = state["schedule"]
        elif method == "DELETE":
            state["schedule"] = None
            data = True
        else:
            data = state["schedule"]
    else:
        state["unexpected_requests"].append((method, p))
        data = []
    await route.fulfill(
        status=status,
        content_type="application/json",
        body=json.dumps({"code": code, "data": data, "msg": message}),
    )
