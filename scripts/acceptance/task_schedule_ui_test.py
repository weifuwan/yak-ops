"""Browser interaction contracts with controlled API responses; not real database evidence."""

import asyncio
import json
import os
import re
from urllib.parse import urlparse, parse_qs
from pathlib import Path
from playwright.async_api import async_playwright, expect

params = {
    "source": {
        "type": "JDBC",
        "datasourceId": "src",
        "database": "app",
        "table": "orders",
    },
    "transform": [],
    "sink": {
        "type": "JDBC",
        "datasourceId": "dst",
        "database": "ods",
        "schema": "public",
        "table": "ods_orders",
        "writeMode": "APPEND",
    },
}
task = {
    "id": "task1",
    "name": "Orders sync",
    "taskType": "DATA_SYNC",
    "status": "PUBLISHED",
    "definitionVersion": 1,
    "parameterSchemaVersion": 2,
    "parameters": json.dumps(params),
    "debug": False,
    "updateBy": "user1",
    "createTime": "2026-10-14T00:00:00",
    "updateTime": "2026-10-14T00:00:00",
}
instance = {
    "id": "i1",
    "definitionId": "task1",
    "definitionVersion": 1,
    "taskType": "DATA_SYNC",
    "triggerType": "SCHEDULE",
    "scheduleId": "s1",
    "scheduleRevision": 2,
    "scheduledTime": "2026-10-14T00:01:00Z",
    "status": "FINISHED",
    "progressPercent": 100,
    "debug": False,
    "createTime": "2026-10-14T00:01:00",
    "startTime": "2026-10-14T00:01:00",
    "endTime": "2026-10-14T00:01:02",
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
    "solution_requests": [],
    "task": task,
}


def paging(rows):
    return {
        "bizData": rows,
        "pagination": {"pageNo": 1, "pageSize": 200, "total": len(rows), "pages": 1},
    }


async def mock(route):
    r = route.request
    p = urlparse(r.url).path
    method = r.method
    b = r.post_data_json if r.post_data else {}
    state["requests"].append((method, p, b, r.headers.get("x-workspace-id")))
    status = 200
    data = None
    code = 200
    if p.endswith("/account/current"):
        data = {
            "id": "user1",
            "userName": "tester",
            "realName": "Tester",
            "status": "ACTIVE",
        }
    elif p == "/api/v1/workspaces":
        data = [
            {"id": "ws1", "name": "Workspace A", "role": "OWNER"},
            {"id": "ws2", "name": "Workspace B", "role": "OWNER"},
        ]
    elif "/user-preferences" in p:
        data = []
    elif "/users/" in p or p.endswith("/user/batch") or p == "/api/v1/user":
        data = []
    elif p == "/api/v1/data-sync/solutions":
        query = parse_qs(urlparse(r.url).query)
        state["solution_requests"].append(query)
        choices = [{"solutionType": "SINGLE_TABLE_OFFLINE", "displayName": "单表离线", "steps": ["FULL"],
                    "defaultSteps": ["FULL"], "allowedCombinations": [{"steps": ["FULL"], "supportsScheduling": True,
                                                                      "writeModes": ["APPEND", "UPSERT"]}]}]
        if query.get("sourceType") == ["MYSQL"]:
            choices.append({"solutionType": "SINGLE_TABLE_REALTIME", "displayName": "单表实时",
                            "steps": ["FULL", "INCREMENTAL"], "defaultSteps": ["FULL", "INCREMENTAL"],
                            "allowedCombinations": [{"steps": ["FULL", "INCREMENTAL"], "supportsScheduling": False,
                                                     "writeModes": ["UPSERT"]}]})
        if state["fail_solutions"]:
            status, code = 503, 500
        else:
            data = choices
    elif p == "/api/v1/data-source/page":
        data = paging(sources)
    elif "/catalog/schemas" in p:
        data = []
    elif "/catalog/tables" in p:
        if "/src/" in p:
            data = [
                {"database": "app", "schema": None, "name": "orders", "type": "TABLE"}
            ]
        else:
            data = [
                {
                    "database": "ods",
                    "schema": "public",
                    "name": "ods_orders",
                    "type": "TABLE",
                }
            ]
    elif p.startswith("/api/v1/data-source/"):
        data = next((x for x in sources if x["id"] == p.rsplit("/", 1)[-1]), {})
    elif p == "/api/v1/task-definitions/page":
        data = paging([state["task"]])
    elif p == "/api/v1/task-definitions" and method == "POST":
        state["created_tasks"] += 1
        state["task"] = {**task, **b, "id": "new-task", "status": "UNPUBLISHED", "parameterSchemaVersion": 3}
        data = state["task"]
    elif p.startswith("/api/v1/task-definitions/"):
        if method == "PUT":
            state["updated_tasks"] += 1
            state["task"] = {**state["task"], **b, **({"parameterSchemaVersion": 3} if "parameters" in b else {})}
        data = state["task"]
    elif p == "/api/v1/task-instances/latest":
        data = (
            [instance]
            if not b["activeOnly"]
            and instance["definitionId"] in b["definitionIds"]
            and r.headers.get("x-workspace-id") == "ws1"
            else []
        )
    elif p == "/api/v1/task-instances/page":
        data = paging([])
    elif p.startswith("/api/v1/task-instances/"):
        if "/logs" in p:
            data = {
                "content": "INFO JDBC query executed\nINFO JDBC sink summary\n",
                "nextCursor": "cursor1",
                "hasMore": False,
            }
        elif p.endswith("/definition"):
            data = {
                "definitionId": "task1",
                "version": 1,
                "name": task["name"],
                "taskType": "DATA_SYNC",
                "parameterSchemaVersion": 2,
                "parameters": task["parameters"],
            }
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
                "after": "2026-10-14T00:00:00Z",
                "nextFireTimes": [f"2026-10-{d:02d}T18:00:00Z" for d in range(14, 19)],
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
        print("UNKNOWN", method, p, flush=True)
        data = []
    await route.fulfill(
        status=status,
        content_type="application/json",
        body=json.dumps({"code": code, "data": data, "msg": "test"}),
    )


async def choose_solution(page, kind, name):
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await expect(dialog).to_be_visible()
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="MySQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(2).click()
    await page.get_by_role("option", name=kind, exact=True).click()
    await dialog.get_by_role("textbox", name="名称", exact=True).fill(name)
    for checkbox in await dialog.get_by_role("checkbox").all():
        await expect(checkbox).to_be_checked()
        await expect(checkbox).to_be_disabled()
    await dialog.get_by_role("button", name="下一步", exact=True).click()


async def verify_solution_creation(page):
    await page.goto("http://127.0.0.1:5173/data-sync")
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="MySQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option", name="单表实时", exact=True)).to_be_visible()
    await page.get_by_role("option", name="单表实时", exact=True).click()
    await dialog.get_by_role("textbox", name="名称", exact=True).fill("User chosen name")
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option", name="单表实时", exact=True)).to_have_count(0)
    await page.get_by_role("option", name="单表离线", exact=True).click()
    await expect(dialog.get_by_role("textbox", name="名称", exact=True)).to_have_value("User chosen name")
    state["fail_solutions"] = True
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="Oracle", exact=True).click()
    await expect(dialog.get_by_text("同步方案加载失败，请重试", exact=True)).to_be_visible()
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    state["fail_solutions"] = False
    await dialog.get_by_role("button", name="重试", exact=True).click()
    await expect(dialog.get_by_role("combobox").nth(2)).to_be_enabled()
    await dialog.get_by_role("button", name="取消", exact=True).click()
    assert state["created_tasks"] == 0
    await page.goto("http://127.0.0.1:5173/data-sync?create=1")
    await choose_solution(page, "单表实时", "CDC draft")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("CDC draft")
    await expect(page.locator("#schedule")).to_have_count(0)
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Restored draft")
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Restored draft")
    await expect(page.locator("#target").get_by_role("combobox").last).to_be_disabled()
    await page.screenshot(path="/tmp/yak-schedule-browser/solution-editor-desktop.png", full_page=True)
    await page.get_by_role("button", name="取消", exact=True).click()
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await dialog.get_by_role("combobox").nth(2).click()
    await page.get_by_role("option", name="单表离线", exact=True).click()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    confirm = page.get_by_role("dialog", name="更改同步方案", exact=True)
    await expect(confirm).to_be_visible()
    await confirm.get_by_role("button", name="取消", exact=True).click()
    assert await page.evaluate("Object.values(sessionStorage).some(v => v.includes('Restored draft'))")
    await dialog.get_by_role("button", name="取消", exact=True).click()
    # Discard the test draft explicitly so the following schedule case starts with its own new draft.
    await page.evaluate("Object.keys(sessionStorage).filter(k => k.startsWith('yak.data-sync.draft:')).forEach(k => sessionStorage.removeItem(k))")


async def configure_cron(page, expression):
    await page.get_by_role("button", name="打开 Cron 调度配置", exact=True).click()
    await page.get_by_role("tab", name="高级 Cron", exact=True).click()
    await page.get_by_role("textbox", name="Cron 表达式", exact=True).fill(expression)
    await page.get_by_role("button", name="确认", exact=True).click()


async def verify_revision_conflict(page):
    await page.get_by_role("button", name="调度", exact=True).click()
    dialog = page.get_by_role("dialog", name="调度", exact=True)
    await configure_cron(page, "0 0 5 * * ?")
    state["conflict"] = True
    await dialog.get_by_role("button", name="保存", exact=True).click()
    await expect(dialog.get_by_text("调度已变更，请重新加载", exact=True)).to_be_visible()
    await expect(dialog.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await expect(dialog.get_by_role("button", name="打开 Cron 调度配置")).to_contain_text("0 0 5 * * ?")
    writes = [x for x in state["requests"] if x[0] == "PUT" and x[1] == "/api/v1/schedules/s1"]
    assert len(writes) == 1, writes
    state["conflict"] = False
    await dialog.get_by_role("button", name="关闭", exact=True).click()
    await page.get_by_role("dialog", name="放弃修改", exact=True).get_by_role("button", name="确认", exact=True).click()
    await expect(page.get_by_role("dialog")).to_have_count(0)


async def verify_partial_task_save(page):
    state["schedule"] = None
    state["fail_schedule"] = True
    await page.goto("http://127.0.0.1:5173/offline-sync/new")
    await choose_solution(page, "单表离线", "Partial save")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    assert state["created_tasks"] == 0
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Partial save")
    for index, name in enumerate(["MySQL source", "PostgreSQL target"]):
        await page.locator("#datasource").get_by_role("combobox").nth(index).click()
        await page.get_by_role("option", name=name, exact=True).click()
    await page.locator("#source").get_by_role("combobox").click()
    await page.get_by_role("option", name="orders", exact=True).click()
    await page.locator("#target").get_by_role("combobox").first.click()
    await page.get_by_role("option", name="public.ods_orders", exact=True).click()
    await configure_cron(page, "0 0 6 * * ?")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile("/data-sync/new-task/edit$"))
    await expect(page.get_by_text(re.compile("任务已保存，调度未确认保存"))).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    assert state["created_tasks"] == 1
    state["fail_schedule"] = False
    await page.get_by_role("button", name="重新加载状态", exact=True).click()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page.get_by_text("同步任务已保存，当前仍为已下线", exact=True)).to_be_visible()
    assert state["created_tasks"] == 1 and state["updated_tasks"] == 0
    assert state["schedule"]["targetId"] == "new-task"
    assert state["schedule"]["cronExpression"] == "0 0 6 * * ?"
    assert state["schedule"]["enabled"] is False


async def main():
    Path("/tmp/yak-schedule-browser").mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, executable_path=os.environ.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE"))
        ctx = await browser.new_context(viewport={"width": 1440, "height": 1000})
        await ctx.add_init_script(
            "localStorage.setItem('yak-ops.current-workspace-id','ws1');"
        )
        page = await ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        await page.route("**/api/**", mock)
        try:
            await page.goto("http://127.0.0.1:5173/offline-sync")
            await expect(
                page.get_by_role("button", name="手动", exact=True)
            ).to_be_visible()
            await expect(
                page.get_by_role("progressbar", name="离线同步", exact=True)
            ).to_have_attribute("aria-valuenow", "100")
            latest_reads = [
                x for x in state["requests"] if x[1] == "/api/v1/task-instances/latest"
            ]
            assert {x[2]["activeOnly"] for x in latest_reads} == {False, True}
            assert all(x[2]["definitionIds"] == ["task1"] for x in latest_reads)
            reads = [
                x for x in state["requests"] if x[1].startswith("/api/v1/schedules/")
            ]
            assert all(x[1].endswith("/page") for x in reads), reads
            await page.get_by_role("button", name="手动", exact=True).click()
            dialog = page.get_by_role("dialog", name="调度", exact=True)
            await expect(dialog).to_be_visible()
            await page.get_by_role(
                "button", name="打开 Cron 调度配置", exact=True
            ).click()
            await page.get_by_role("tab", name="高级 Cron", exact=True).click()
            cron = page.get_by_role("textbox", name="Cron 表达式", exact=True)
            expression = "0 0 2 ? * MON#5 2030"
            await cron.fill(expression)
            await page.get_by_role("tab", name="常用配置", exact=True).click()
            await expect(cron).to_have_value(expression)
            await expect(page.get_by_text("该表达式无法转换为常用配置")).to_be_visible()
            await page.get_by_role("button", name="确认", exact=True).click()
            await dialog.get_by_role("button", name="保存", exact=True).click()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).not_to_be_checked()
            assert state["schedule"]["enabled"] is False
            assert state["schedule"]["cronExpression"] == expression
            assert not any(x[1].endswith("/enable") for x in state["requests"])
            await dialog.get_by_role("switch", name="启用调度", exact=True).click()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).to_be_checked()
            await expect(
                dialog.get_by_role("button", name="打开 Cron 调度配置")
            ).to_be_disabled()
            await dialog.get_by_role("switch", name="启用调度", exact=True).click()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).not_to_be_checked()
            state["fires"] = [
                {
                    "id": "f" + str(i),
                    "scheduleId": "s1",
                    "scheduleRevision": 2,
                    "scheduledTime": "2026-10-14T00:01:00Z",
                    "fireTime": "2026-10-14T00:01:01Z",
                    "resolvedTime": "2026-10-14T00:01:01Z",
                    "outcome": outcome,
                    "reason": reason,
                    "instanceId": instance,
                }
                for i, (outcome, reason, instance) in enumerate(
                    [
                        ("ADMITTED", None, "i1"),
                        ("SKIPPED", "RESOURCE_BUSY", None),
                        ("REJECTED", "STALE_SCHEDULE", None),
                        ("SUBMISSION_FAILED", "PREPARATION_FAILED", None),
                    ]
                )
            ]
            await dialog.get_by_role("tab", name="触发记录", exact=True).click()
            await expect(dialog.get_by_text("已跳过", exact=True)).to_be_visible()
            assert await dialog.get_by_role("button", name="查看实例").count() == 1
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-history-desktop.png",
                full_page=True,
            )
            await dialog.get_by_role("button", name="查看实例").click()
            await expect(page).to_have_url(re.compile(r"/data-sync/task1/detail\?executionId=i1"))
            await expect(page.get_by_text("调度版本：2")).to_be_visible()
            await page.get_by_role("tab", name="运行日志", exact=True).click()
            await expect(
                page.get_by_text(re.compile("INFO JDBC query executed"))
            ).to_be_visible()
            await page.get_by_role("tab", name="执行状态", exact=True).click()
            await page.get_by_role("button", name="触发记录", exact=True).click()
            await expect(
                page.get_by_role("dialog", name="触发记录", exact=True)
            ).to_be_visible()
            await page.set_viewport_size({"width": 390, "height": 844})
            await page.emulate_media(reduced_motion="reduce")
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-history-mobile.png",
                full_page=True,
            )
            await page.keyboard.press("Escape")
            await expect(page.get_by_role("dialog")).to_have_count(0)
            await page.set_viewport_size({"width": 1440, "height": 1000})
            await verify_revision_conflict(page)
            await verify_solution_creation(page)
            await verify_partial_task_save(page)
            state["fail_batch"] = True
            await page.goto("http://127.0.0.1:5173/offline-sync")
            await expect(page.get_by_role("button", name="调度加载失败", exact=True)).to_be_visible()
            await expect(page.get_by_role("button", name="手动", exact=True)).to_have_count(0)
            state["fail_batch"] = False
            await page.get_by_role("button", name="Workspace A", exact=True).click()
            await page.get_by_role("menuitem", name="Workspace B", exact=True).click()
            await expect(page.get_by_role("button", name="手动", exact=True)).to_be_visible()
            assert [x for x in state["requests"] if x[1] == "/api/v1/schedules/page"][-1][3] == "ws2"
            assert not errors, errors
            print(
                "PASS browser: batched schedule and execution lists; execution progress; advanced Cron preservation; save-without-enable; enable/disable; four terminal outcomes; historical link; instance/log tabs; narrow viewport; Escape; reduced motion; revision conflict; partial save retry; failed batch; Workspace isolation",
                flush=True,
            )
        except:
            print((await page.locator("body").inner_text())[:16000])
            print("ERRORS", errors)
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-failure.png", full_page=True
            )
            raise
        finally:
            await browser.close()


asyncio.run(main())
