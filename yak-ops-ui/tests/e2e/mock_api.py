"""Deterministic HTTP fixtures for UI interaction tests; never evidence for backend E2E."""

import json
import re
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
    "revision": 1,
    "publishedVersion": 1,
    "publishedDebug": False,
    "hasUnpublishedChanges": False,
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
    "publications": [],
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


def definition_response(definition):
    """Expose draft and published state separately, as the definition HTTP API does."""
    return {
        **definition,
        "hasUnpublishedChanges": (
            definition["definitionVersion"] != definition.get("publishedVersion")
            or definition["debug"] != definition.get("publishedDebug")
        ),
    }


def version_response(definition, include_parameters=False):
    result = {
        "definitionId": definition["id"],
        "version": definition["definitionVersion"],
        "parameterSchemaVersion": definition["parameterSchemaVersion"],
        "createBy": definition.get("updateBy", "user1"),
        "createTime": definition["updateTime"],
    }
    if include_parameters:
        result["parameters"] = deepcopy(definition["parameters"])
    return result


def task_profile(definition):
    config = definition["parameters"]
    if config["source"].get("startup") or config["pipeline"]["execution.runtime-mode"] == "STREAMING":
        return "DATABASE_REPLICATION"
    return "SINGLE_TABLE_BATCH"


def connector_capabilities(source_type, target_type, cdc):
    modes = ["APPEND", "UPSERT"]
    if target_type == "MYSQL":
        modes.append("RETRACT" if cdc else "REPLACE")
    return {
        "maxTables": 100,
        "sourceRead": not cdc, "explicitSplitKey": not cdc, "dataPreview": not cdc,
        "sqlHooks": not cdc,
        "regexSelection": cdc and target_type in ("MYSQL", "POSTGRE_SQL"),
        "snapshotFilter": cdc, "snapshotSkip": cdc, "dmlEvents": cdc,
        "typeMapping": True,
        "writeModes": modes,
        "sourceEngineKeys": [] if cdc else ["fetchSize", "queryTimeoutSeconds"],
        "sinkEngineKeys": ["batchSize"],
        "typeMappingRules": [
            {"sourceType": "INT", "targetTypes": ["BIGINT"], "conditional": False},
            {"sourceType": "VARCHAR", "targetTypes": ["VARCHAR(255)", "VARCHAR(1024)", "VARCHAR(65535)"], "conditional": True},
        ],
    }


def catalog_tables(path):
    mode = state["catalog_mode"]
    if "/src/" in path:
        names = state.get("source_tables", ["orders"] if mode == "single" else (
            ["sm_browser_source"] if mode == "schema" else ["batch_a", "batch_b"]))
        database, schema = "app", None
    else:
        names = state.get("target_tables", ["ods_orders"] if mode == "single" else (
            [] if mode == "schema" else ["batch_a_copy", "batch_b_copy"]))
        datasource = next(source for source in state.get("data_sources", sources)
                          if source["id"] == path.split("/")[4])
        database, schema = datasource["database"], datasource.get("schema")
    return [item if isinstance(item, dict) else
            {"database": database, "schema": schema, "name": item, "type": "TABLE"}
            for item in names]


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
                            "supportsIncrementalBatch": source_type in ("MYSQL", "POSTGRE_SQL"),
                            "capabilities": connector_capabilities(source_type, target_type, False)})
            if source_type == "MYSQL":
                choices.append({**capabilities, "sourceConnector": "mysql", "producesChangelog": True,
                                "boundedness": "CONTINUOUS_UNBOUNDED", "supportsIncrementalBatch": False,
                                "capabilities": connector_capabilities(source_type, target_type, True)})
        if state["fail_solutions"]:
            status, code = 503, 500
        else:
            data = choices
    elif p == "/api/v1/data-source/page":
        data = paging(state.get("data_sources", sources))
    elif "/catalog/schemas" in p:
        data = []
    elif "/catalog/databases" in p:
        data = sorted({row["database"] for row in catalog_tables(p)})
    elif "/catalog/columns" in p:
        data = state.get("source_columns" if "/src/" in p else "target_columns", [
            {"name": "id", "typeName": "BIGINT", "nullable": False, "primaryKey": True}
        ])
    elif "/catalog/table-page" in p:
        gate = state.get("table_page_gate")
        if gate is not None and b.get("tablePattern"):
            state["table_page_started"].set()
            await gate.wait()
        try:
            tables = [row for row in catalog_tables(p)
                      if (not b.get("database") or row.get("database") == b["database"])
                      and (not b.get("schema") or row.get("schema") == b["schema"])
                      and (not b.get("keyword") or b["keyword"].lower() in row["name"].lower())
                      and (not b.get("databasePattern") or re.fullmatch(b["databasePattern"], row.get("database") or ""))
                      and (not b.get("tablePattern") or re.fullmatch(b["tablePattern"], row["name"]))]
            offset, limit = b.get("offset", 0), min(b.get("limit", 100), state.get("catalog_page_size", 100))
            data = {"items": tables[offset:offset + limit],
                    "nextOffset": offset + limit if offset + limit < len(tables) else None}
        except re.error:
            status, code, message = 400, 400, "库表过滤正则无效"
    elif "/catalog/preview" in p:
        if state.get("fail_preview"):
            status, code, message = 400, 400, "数据预览失败"
        else:
            data = deepcopy(state.get("preview", {
                "columns": [{"name": "id", "typeName": "BIGINT"}, {"name": "amount", "typeName": "DECIMAL"}],
                "rows": [["9007199254740993", "123456789012345.678900"]], "truncated": False,
            }))
    elif "/catalog/tables" in p:
        data = catalog_tables(p)
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
        data = paging([definition_response(row) for row in rows], b.get("pageNo", 1), b.get("pageSize", 20))
    elif p == "/api/v1/task-definitions" and method == "POST":
        state["created_tasks"] += 1
        state["task"] = definition_response({
            **task, **b, "id": "new-task", "status": "UNPUBLISHED",
            "definitionVersion": 1, "parameterSchemaVersion": 8, "revision": 1,
            "publishedVersion": None, "publishedDebug": None,
        })
        remember_definition(state["task"])
        data = state["task"]
    elif p.startswith("/api/v1/task-definitions/"):
        definition = state["task"]
        remember_definition(definition)
        path = p.removeprefix("/api/v1/task-definitions/").split("/")
        if path[0] != definition["id"]:
            status, code = 404, 404
        elif len(path) > 1 and path[1] == "versions":
            if len(path) == 2:
                query = parse_qs(urlparse(r.url).query)
                rows = [version_response(value) for (definition_id, _), value in state["versions"].items()
                        if definition_id == definition["id"]]
                rows.sort(key=lambda row: row["version"], reverse=True)
                data = paging(rows, int(query.get("pageNo", [1])[0]), int(query.get("pageSize", [20])[0]))
            else:
                version = state["versions"].get((definition["id"], int(path[2])))
                if version:
                    data = version_response(version, include_parameters=True)
                else:
                    status, code = 404, 404
        elif p.endswith("/publications"):
            query = parse_qs(urlparse(r.url).query)
            rows = [receipt for receipt in reversed(state["publications"])
                    if receipt["definitionId"] == definition["id"]]
            data = paging(rows, int(query.get("pageNo", [1])[0]), int(query.get("pageSize", [20])[0]))
        elif p.endswith("/publish"):
            receipt = next((entry for entry in state["publications"]
                            if entry["definitionId"] == definition["id"]
                            and entry["requestId"] == b.get("requestId")), None)
            if (not isinstance(b.get("version"), int) or b["version"] < 1
                    or not isinstance(b.get("expectedRevision"), int) or b["expectedRevision"] < 1
                    or not re.fullmatch(r"[A-Za-z0-9_-]{8,128}", b.get("requestId", ""))):
                status, code, message = 400, 400, "发布请求缺少版本、revision 或 requestId"
            elif receipt:
                if (receipt["version"] != b["version"]
                        or receipt["sourceRevision"] != b["expectedRevision"]):
                    status, code, message = 409, 19002, "发布请求标识已用于其他请求"
            elif b["expectedRevision"] != definition["revision"]:
                status, code, message = 409, 19001, "任务已变更，请重新加载后重试"
            elif (definition["id"], b["version"]) not in state["versions"]:
                status, code = 404, 404
            elif state["fail_publish"]:
                status, code = 400, 19003
                message = "同步配置校验失败"
            else:
                state["task"] = definition_response({
                    **definition, "status": "PUBLISHED", "publishedVersion": b["version"],
                    "publishedDebug": definition["debug"], "revision": definition["revision"] + 1,
                })
                state["publications"].append({
                    "id": "publication-" + str(len(state["publications"]) + 1),
                    "definitionId": definition["id"], "version": b["version"],
                    "debug": definition["debug"], "requestId": b["requestId"],
                    "sourceRevision": b["expectedRevision"], "createBy": "user1",
                    "createTime": "2030-10-14T00:02:00",
                })
            if status == 200:
                data = definition_response(state["task"])
        elif p.endswith("/unpublish"):
            state["task"] = definition_response({
                **definition, "status": "UNPUBLISHED", "publishedVersion": None,
                "publishedDebug": None, "revision": definition["revision"] + 1,
            })
            data = state["task"]
        elif p.endswith("/instances"):
            if definition["status"] != "PUBLISHED" or definition.get("publishedVersion") is None:
                status, code = 400, 400
            else:
                instance.update({"id": "i1", "definitionId": definition["id"],
                                 "definitionVersion": definition["publishedVersion"],
                                 "debug": definition["publishedDebug"],
                                 "triggerType": "MANUAL", "scheduleId": None,
                                 "scheduleRevision": None, "scheduledTime": None,
                                 "status": "RUNNING", "progressPercent": 0,
                                 "endTime": None, "executionReport": state.get("run_report")})
                state["show_instances"] = True
                status, data = 202, {"instanceId": instance["id"]}
        elif method == "PUT":
            if not isinstance(b.get("expectedRevision"), int) or b["expectedRevision"] < 1:
                status, code, message = 400, 400, "保存请求缺少 expectedRevision"
            elif b["expectedRevision"] != definition["revision"]:
                status, code, message = 409, 19001, "任务已变更，请重新加载后重试"
            else:
                state["updated_tasks"] += 1
                changed = "parameters" in b and (b["parameters"] != definition["parameters"]
                                                  or definition["parameterSchemaVersion"] != 8)
                fields = {key: value for key, value in b.items() if key != "expectedRevision"}
                state["task"] = definition_response({
                    **definition, **fields,
                    "definitionVersion": definition["definitionVersion"] + int(changed),
                    "revision": definition["revision"] + 1,
                    **({"parameterSchemaVersion": 8} if "parameters" in b else {}),
                })
                remember_definition(state["task"])
                data = state["task"]
        else:
            data = definition_response(definition)
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
