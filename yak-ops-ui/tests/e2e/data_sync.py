"""UI contracts for sync solution selection and schedule-save recovery (mocked APIs)."""
import asyncio
from copy import deepcopy
import json
import os
import re
from pathlib import Path
from playwright.async_api import expect
from mock_api import instance, mock, state

def site(path):
    return os.environ["YAK_UI_BASE_URL"].rstrip("/") + path


async def close_notifications(page):
    close = page.get_by_role("button", name="Close notification", exact=True)
    for _ in range(await close.count()):
        count = await close.count()
        if not count:
            break
        await close.first.click()
        await expect(close).to_have_count(count - 1)

async def choose_solution(page, kind, name, target_type="PostgreSQL"):
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await expect(dialog).to_be_visible()
    for index, option in enumerate(("MySQL", target_type, kind)):
        await pick_option(page, dialog.get_by_role("combobox").nth(index), option)
    await dialog.get_by_role("textbox", name="名称", exact=True).fill(name)
    await expect(dialog.get_by_role("checkbox")).to_have_count(0)
    await dialog.get_by_role("button", name="下一步", exact=True).click()


async def verify_solution_creation(page):
    await page.goto(site("/data-sync?create=1"))
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    for index in (0, 1):
        await pick_option(page, dialog.get_by_role("combobox").nth(index), "MySQL")
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option")).to_have_count(2)
    await expect(page.get_by_role("option", name="单表离线", exact=True)).to_be_visible()
    await page.get_by_role("option", name="整库实时", exact=True).click()
    await dialog.get_by_role("textbox", name="名称", exact=True).fill("User chosen name")
    await pick_option(page, dialog.get_by_role("combobox").nth(0), "PostgreSQL")
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option", name="整库实时", exact=True)).to_have_count(0)
    await page.get_by_role("option", name="单表离线", exact=True).click()
    await expect(dialog.get_by_role("textbox", name="名称", exact=True)).to_have_value("User chosen name")
    state["fail_solutions"] = True
    await pick_option(page, dialog.get_by_role("combobox").nth(1), "Oracle")
    await expect(dialog.get_by_text("同步方案加载失败，请重试", exact=True)).to_be_visible()
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    state["fail_solutions"] = False
    await dialog.get_by_role("button", name="重试", exact=True).click()
    await expect(dialog.get_by_role("combobox").nth(2)).to_be_enabled()
    await dialog.get_by_role("button", name="取消", exact=True).click()
    assert state["created_tasks"] == 0

    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "整库实时", "CDC draft")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("CDC draft")
    await expect(page.locator("#schedule")).to_have_count(0)
    await expect(page.locator("#field-mapping")).to_have_count(0)
    await expect(page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True)).to_have_count(2)
    await expect(page.locator("#env").get_by_text("持续运行", exact=True)).to_be_visible()
    schema_switch = page.get_by_role("switch", name="同步结构变更", exact=True)
    await expect(schema_switch).to_be_checked()
    await schema_switch.click()
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Restored draft")
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Restored draft")
    await expect(schema_switch).not_to_be_checked()
    await pick_option(page, page.get_by_role("combobox", name="读取策略", exact=True), "全量后结束")
    await expect(page.locator("#schedule")).to_be_visible()
    await expect(schema_switch).to_have_count(0)
    await page.reload()
    await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_contain_text("全量后结束")
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "solution-editor-desktop.png"), full_page=True)

    # Re-selecting the same product restores the complete strategy-bearing draft.
    await page.get_by_role("button", name="取消", exact=True).click()
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await pick_option(page, dialog.get_by_role("combobox").nth(2), "单表离线")
    await pick_option(page, dialog.get_by_role("combobox").nth(2), "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_contain_text("全量后结束")
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Restored draft")
    await page.get_by_role("button", name="取消", exact=True).click()
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    await pick_option(page, dialog.get_by_role("combobox").nth(2), "单表离线")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    confirm = page.get_by_role("dialog", name="更改同步方案", exact=True)
    await expect(confirm).to_be_visible()
    await confirm.get_by_role("button", name="取消", exact=True).click()
    assert await page.evaluate("Object.values(sessionStorage).some(v => v.includes('Restored draft'))")
    await dialog.get_by_role("button", name="取消", exact=True).click()

    await page.get_by_role("button", name="Workspace A", exact=True).click()
    await page.get_by_role("menuitem", name="Workspace B", exact=True).click()
    await expect(page.get_by_role("button", name="Workspace B", exact=True)).to_be_visible()
    assert not await page.evaluate("Object.keys(sessionStorage).some(k => k.startsWith('yak.data-sync.draft:'))")
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    await expect(dialog.get_by_role("textbox", name="名称", exact=True)).to_have_value("")
    await choose_solution(page, "单表离线", "Workspace B draft")
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Workspace B draft")
    await page.get_by_role("button", name=re.compile("Tester")).click()
    await page.get_by_role("menuitem", name="退出登录", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/login(?:\?|$)"))
    assert not await page.evaluate("Object.keys(sessionStorage).some(k => k.startsWith('yak.data-sync.draft:'))")
    assert [request for request in state["requests"] if request[1].endswith("/account/logout")][-1][3] is None


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
    await page.goto(site("/data-sync/new"))
    await choose_solution(page, "单表离线", "Partial save")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    assert state["created_tasks"] == 0
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Partial save")
    await pick_datasources(page)
    await pick_option(page, page.locator("#source").get_by_role("combobox", name="表", exact=True), "orders")
    await pick_option(page, page.locator("#target").get_by_role("combobox", name="表", exact=True), "public.ods_orders")
    await configure_cron(page, "0 0 6 * * ?")

    gate = asyncio.Event()
    started = asyncio.Event()
    async def hold_creation(route):
        started.set()
        await gate.wait()
        await mock(route)
    await page.route("**/api/v1/task-definitions", hold_creation)
    try:
        await page.get_by_role("button", name="保存", exact=True).click()
        await asyncio.wait_for(started.wait(), timeout=10)
        await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_be_disabled()
        await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_be_disabled()
    finally:
        gate.set()
    await expect(page).to_have_url(re.compile("/data-sync/new-task/edit$"))
    await page.unroute("**/api/v1/task-definitions", hold_creation)
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

    await close_notifications(page)
    state["fail_publish"] = True
    await page.get_by_role("button", name="保存并上线", exact=True).click()
    await expect(page.get_by_text(re.compile("任务已保存，仍处于下线状态。上线失败"))).to_be_visible()
    assert state["task"]["status"] == "UNPUBLISHED"
    assert state["created_tasks"] == 1 and state["updated_tasks"] == 0
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Partial save")
    await close_notifications(page)
    state["fail_publish"] = False
    await page.get_by_role("button", name="保存并上线", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync$"))
    assert state["task"]["status"] == "PUBLISHED"
    assert state["created_tasks"] == 1


async def pick_option(page, trigger, option):
    """Choose a visible option and wait until the prior dropdown closes."""
    await trigger.click()
    popup = page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]').last
    listbox = popup.get_by_role("listbox")
    list_id = await listbox.get_attribute("id")
    assert list_id, "Select listbox must have an ID"
    await listbox.get_by_role("option", name=option, exact=True).click()
    await expect(page.locator(f'[id="{list_id}"]')).to_be_hidden()
    await expect(page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]')).to_have_count(0)


async def prepare_database_task(page, kind):
    await page.goto(site("/data-sync?create=1"))
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await expect(dialog).to_be_visible()
    for index, name in enumerate(("MySQL", "PostgreSQL", kind)):
        await pick_option(page, dialog.get_by_role("combobox").nth(index), name)
    await dialog.get_by_role("textbox", name="名称", exact=True).fill("playwright-" + kind)
    return dialog


async def pick_datasources(page):
    for index, name in enumerate(("MySQL source", "PostgreSQL target")):
        await pick_option(page, page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True).nth(index), name)


def batch_write_mode(page):
    """Select the Sink-wide write-mode control, not the target datasource."""
    return page.locator("#target").get_by_role("combobox", name="批量设置写入方式", exact=True)


async def verify_multitable_mapping(page):
    """Replication selection, duplicate guard, draft restoration and saved mapping."""
    import json
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    for table in ("batch_a", "batch_b"):
        await page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
        await pick_option(
            page,
            page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + table + r"$")),
            re.compile(r"(.*\.)?batch_a_copy$"))
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await expect(page.get_by_text("去向表不能重复", exact=True).first).to_be_visible()
    await pick_option(
        page,
        page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?batch_b$")),
        re.compile(r"(.*\.)?batch_b_copy$"))
    await expect(batch_write_mode(page)).to_be_disabled()
    await page.reload()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    requests = [req[2] for req in state["requests"]
                if req[0] == "POST" and req[1] == "/api/v1/task-definitions"]
    assert len(requests) == 1, requests
    parameters = requests[0]["parameters"]
    assert parameters["source"]["type"] == "mysql" and len(parameters["route"]) == 2, parameters
    assert parameters["source"]["startup"] == {"mode": "INITIAL"}
    assert [row["table"] for row in parameters["source"]["tables"]] == ["batch_a", "batch_b"]
    assert [row["table"] for row in parameters["sink"]["tables"]] == ["batch_a_copy", "batch_b_copy"]
    assert all(row["write"]["mode"] == "UPSERT" for row in parameters["sink"]["tables"])



async def verify_multitable_selection(page):
    """Filter source tables, choose several, remove a mapping, and save the remaining table."""
    import json

    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_option(page, page.get_by_role("combobox", name="读取策略", exact=True), "全量后结束")
    await pick_datasources(page)

    source = page.locator("#source")
    search = source.get_by_placeholder("搜索来源表", exact=True)
    checkbox_a = source.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?batch_a$"))
    checkbox_b = source.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?batch_b$"))

    await search.fill("batch_b")
    await expect(checkbox_a).to_have_count(0)
    await source.get_by_role("button", name="选择当前结果", exact=True).click()
    await expect(checkbox_b).to_be_checked()
    await expect(page.get_by_text("已选 1 / 100", exact=True)).to_be_visible()

    await search.fill("")
    await checkbox_a.check()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()

    await checkbox_b.uncheck()
    await expect(page.get_by_text("已选 1 / 100", exact=True)).to_be_visible()
    await checkbox_b.check()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()

    await page.locator("#target").get_by_role("row").filter(
        has_text=re.compile(r"(?:^|\.)batch_b")
    ).get_by_role("button", name="移除", exact=True).click()
    await expect(checkbox_b).not_to_be_checked()
    await expect(page.get_by_text("已选 1 / 100", exact=True)).to_be_visible()

    await pick_option(
        page,
        page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?batch_a$")),
        re.compile(r"(.*\.)?batch_a_copy$"),
    )
    await page.reload()
    await expect(page.get_by_text("已选 1 / 100", exact=True)).to_be_visible()
    await expect(source.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?batch_b$"))).not_to_be_checked()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))

    writes = [item[2] for item in state["requests"]
              if item[0] == "POST" and item[1] == "/api/v1/task-definitions"]
    assert len(writes) == 1, writes
    saved = writes[0]["parameters"]
    assert saved["transform"] == [] and saved["pipeline"]["execution.runtime-mode"] == "BATCH"
    assert saved["source"]["startup"] == {"mode": "SNAPSHOT"}
    assert [entry["table"] for entry in saved["source"]["tables"]] == ["batch_a"]
    assert [entry["table"] for entry in saved["sink"]["tables"]] == ["batch_a_copy"]


async def verify_schema_configuration(page):
    """Target preparation is an editor policy, without a speculative DDL preview."""
    state["catalog_mode"] = "schema"
    dialog = await prepare_database_task(page, "单表离线")
    await expect(dialog.get_by_role("checkbox")).to_have_count(0)
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    await pick_option(page, page.locator("#source").get_by_role("combobox", name="表", exact=True),
                      re.compile(r"(.*\.)?sm_browser_source$"))
    target = page.locator("#target")
    await expect(target.get_by_role("combobox", name="目标表结构", exact=True)).to_contain_text("校验已有表")
    await pick_option(page, target.get_by_role("combobox", name="目标表结构", exact=True), "创建或调整目标表")
    schema_select = target.get_by_role("combobox", name="Schema", exact=True)
    if await schema_select.count():
        await pick_option(page, schema_select, "public")
    field = target.get_by_placeholder("去向表名称", exact=True)
    await expect(field).to_be_enabled()
    await expect(page.get_by_role("button", name="DDL 预览", exact=True)).to_have_count(0)
    for table in ("sm_browser_unused", "sm_browser_target"):
        await field.fill(table)
    assert state["created_tasks"] == 0
    assert not any(req[1] == "/api/v1/data-sync/schema/preview" for req in state["requests"])
    await page.reload()
    await expect(field).to_have_value("sm_browser_target")
    await expect(target.get_by_role("combobox", name="目标表结构", exact=True)).to_contain_text("创建或调整目标表")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    created = [req[2] for req in state["requests"]
               if req[0] == "POST" and req[1] == "/api/v1/task-definitions"][-1]
    config = created["parameters"]
    assert config["source"]["syncMode"] == "FULL"
    assert config["sink"]["schemaPolicy"]["initial"] == "CREATE_OR_ALTER"
    assert config["sink"]["tables"][0]["table"] == "sm_browser_target"


async def verify_realtime_stages(page):
    """Database realtime setup and progress display, without a live CDC consumer."""
    import json
    from datetime import datetime, timezone
    from mock_api import instance
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_contain_text("全量后持续增量")
    await pick_datasources(page)
    for table in ("batch_a", "batch_b"):
        await page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
        await pick_option(page,
                          page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + table + r"$")),
                          re.compile(r"(.*\.)?" + table + r"_copy$"))
    await expect(batch_write_mode(page)).to_be_disabled()
    await expect(page.locator("#schedule")).to_have_count(0)
    await page.reload()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    saved = [item[2] for item in state["requests"]
             if item[0] == "POST" and item[1] == "/api/v1/task-definitions"][-1]
    config = saved["parameters"]
    assert config["source"]["type"] == "mysql" and len(config["route"]) == 2, config
    assert config["source"]["startup"]["mode"] == "INITIAL"
    assert len(config["sink"]["tables"]) == 2
    assert all(row["write"]["mode"] == "UPSERT" for row in config["sink"]["tables"])

    # Deliberately synthesize Instance telemetry; this must not start MySQL CDC or wait for a checkpoint.
    full = {"step": "FULL", "status": "FINISHED", "progressPercent": 100,
            "progressUnit": "SPLITS", "readRows": 4, "committedRows": 4,
            "plannedSplits": 1, "completedSplits": 1, "failureCode": None}
    incremental = {"step": "INCREMENTAL", "status": "RUNNING", "progressPercent": None,
                   "progressUnit": "RECORDS", "readRows": 5, "committedRows": 5,
                   "plannedSplits": None, "completedSplits": None, "failureCode": None}
    report = {"version": 2, "observedAt": datetime.now(timezone.utc).isoformat(),
              "generation": 0, "restored": False, "counterScope": "GENERATION",
              "phase": "STREAMING", "checkpoints": {"handoffId": 1, "completedId": 1},
              "stages": [full, incremental],
              "tables": [{"mappingIndex": index, "stages": [full, incremental]}
                         for index in range(2)]}
    state["task"]["status"] = "PUBLISHED"
    state["show_instances"] = True
    instance.update({"definitionId": "new-task", "status": "RUNNING",
                     "executionReport": json.dumps(report), "progressPercent": None})
    await page.goto(site("/data-sync/new-task/detail?executionId=i1"))
    await expect(page.get_by_role("heading", name="整库实时 · 全量后持续增量", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="调度", exact=True)).to_have_count(0)
    await expect(page.get_by_role("progressbar", name="全量同步", exact=True)).to_have_attribute(
        "aria-valuenow", "100")
    streaming = page.get_by_role("progressbar", name="增量同步", exact=True)
    await expect(streaming).to_have_attribute("data-mode", "running")
    assert await streaming.get_attribute("aria-valuenow") is None

    # Stale sampled data cannot keep a false healthy streaming indicator.
    report["observedAt"] = "2000-01-01T00:00:00Z"
    instance["executionReport"] = json.dumps(report)
    await page.reload()
    await expect(page.get_by_role("progressbar", name="增量同步", exact=True)).to_have_attribute(
        "aria-valuetext", "观测已过期")



async def verify_editor_round_trip(page):
    """Metadata-only updates, parameter edits and explicit single-table keyless replication."""
    from mock_api import params, sources, task

    config = deepcopy(params)
    config["source"]["type"] = "mysql"
    del config["source"]["syncMode"]
    config["sink"]["tables"][0]["write"] = {"mode": "UPSERT"}
    config["sink"]["schemaPolicy"]["evolution"] = {"enabled": True, "allowDropColumns": True}
    config["pipeline"] = {
        "execution.runtime-mode": "STREAMING", "pipeline.table-scope": "DATABASE",
        "schema.change.behavior": "evolve", "name": "orders-pipeline",
        "parallelism": 2, "local-time-zone": "Asia/Shanghai",
    }
    state["task"] = {**task, "status": "UNPUBLISHED", "parameters": config}
    await page.goto(site("/data-sync/task1/edit"))
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value(task["name"])
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await expect(page.locator("#env").get_by_text("持续运行", exact=True)).to_be_visible()
    assert any("/src/catalog/columns" in request[1] for request in state["requests"])
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Renamed replication")
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    metadata_request = [request[2] for request in state["requests"]
                        if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/task1"][-1]
    assert "parameters" not in metadata_request
    assert state["task"]["parameters"] == config
    assert state["task"]["definitionVersion"] == 1

    schema_switch = page.get_by_role("switch", name="同步结构变更", exact=True)
    await expect(schema_switch).to_be_checked()
    for enabled in (False, True):
        await schema_switch.click()
        async with page.expect_response(lambda response: response.request.method == "PUT"
                                        and response.url.endswith("/api/v1/task-definitions/task1")):
            await page.get_by_role("button", name="保存", exact=True).click()
        saved = [request[2]["parameters"] for request in state["requests"]
                 if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/task1"
                 and "parameters" in request[2]][-1]
        assert saved["source"]["startup"] == {"mode": "INITIAL"}, saved
        assert "syncMode" not in saved["source"]
        assert saved["pipeline"]["execution.runtime-mode"] == "STREAMING"
        assert saved["sink"]["schemaPolicy"]["evolution"] == {
            "enabled": enabled, "allowDropColumns": False,
        }
        assert saved["pipeline"]["schema.change.behavior"] == ("evolve" if enabled else "exception")
        for key in ("name", "parallelism", "local-time-zone"):
            assert saved["pipeline"][key] == config["pipeline"][key]
        await page.reload()
        await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
        if enabled:
            await expect(schema_switch).to_be_checked()
        else:
            await expect(schema_switch).not_to_be_checked()

    positioned = deepcopy(state["task"]["parameters"])
    positioned["source"]["startup"] = {
        "mode": "SPECIFIC_OFFSETS", "file": "mysql-bin.000007", "pos": 456,
        "gtidSet": "24bc7850-2c16-11e6-a073-0242ac110002:1-12", "skipEvents": 2, "skipRows": 1,
    }
    state["task"] = {**state["task"], "parameters": positioned,
                     "definitionVersion": state["task"]["definitionVersion"] + 1}
    await page.reload()
    strategy = page.get_by_role("combobox", name="读取策略", exact=True)
    await expect(strategy).to_contain_text("仅增量")
    await pick_option(page, strategy, "仅增量")
    await schema_switch.click()
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    assert state["task"]["parameters"]["source"]["startup"] == positioned["source"]["startup"]

    state["data_sources"] = [*sources, {
        "id": "mysql-dst", "name": "MySQL target", "dbType": "MYSQL", "database": "ods",
    }]
    state["source_tables"] = ["orders", "other_orders"]
    state["source_columns"] = [
        {"name": "bucket", "typeName": "INT", "nullable": False, "primaryKey": False},
        {"name": "payload", "typeName": "VARCHAR", "nullable": True, "primaryKey": False},
    ]
    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "整库实时", "Keyless replication", target_type="MySQL")
    for index, label in enumerate(("MySQL source", "MySQL target")):
        await pick_option(page, page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True).nth(index), label)
    await page.get_by_role("checkbox", name="选择 app.orders", exact=True).check()
    await pick_option(page, page.get_by_role("combobox", name="去向表 app.orders", exact=True), "ods.ods_orders")
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await schema_switch.click()
    await expect(schema_switch).not_to_be_checked()
    await pick_option(page, page.get_by_role("combobox", name="快照分片字段", exact=True), "bucket")
    # An explicit change of write identity must not retain hidden target-key overrides.
    await page.get_by_role("textbox", name="目标主键", exact=True).fill("bucket")
    writer = page.get_by_role("combobox", name="写入方式 app.orders", exact=True)
    await pick_option(page, writer, "逐行更新")
    await expect(page.get_by_role("textbox", name="目标主键", exact=True)).to_have_count(0)
    await expect(page.get_by_role("checkbox", name="选择 app.other_orders", exact=True)).to_be_disabled()
    await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_be_disabled()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    keyless = state["task"]["parameters"]
    assert keyless["source"]["tables"][0]["snapshot"] == {"chunkKeyColumn": "bucket"}
    assert keyless["pipeline"]["pipeline.table-scope"] == "SINGLE"
    assert keyless["sink"]["tables"][0]["write"] == {
        "mode": "RETRACT", "transaction": {"mode": "CHECKPOINT_XA"},
    }
    await page.reload()
    await expect(writer).to_contain_text("逐行更新")
    await expect(page.get_by_role("combobox", name="快照分片字段", exact=True)).to_contain_text("bucket")
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()


async def verify_field_mapping(page):
    """Author field aliases and expressions, then reopen without reconstructing their text."""
    state["source_tables"] = ["orders", "orders_archive"]
    state["source_columns"] = [
        {"name": "id", "typeName": "BIGINT", "nullable": False, "primaryKey": True},
        {"name": "amount", "typeName": "DECIMAL", "nullable": False, "primaryKey": False},
        {"name": "memo", "typeName": "VARCHAR", "nullable": True, "primaryKey": False},
    ]
    state["target_columns"] = [
        {"name": "order_id", "typeName": "BIGINT", "nullable": False, "primaryKey": True},
        {"name": "amount", "typeName": "DECIMAL", "nullable": False, "primaryKey": False},
    ]
    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "单表离线", "Mapped orders")
    await pick_datasources(page)
    source_select = page.locator("#source").get_by_role("combobox", name="表", exact=True)
    await pick_option(page, source_select, "orders")
    await pick_option(page, page.locator("#target").get_by_role("combobox", name="表", exact=True), "public.ods_orders")
    fields = page.locator("#field-mapping")
    await expect(fields.get_by_role("checkbox", name="同步字段 id", exact=True)).to_be_checked()
    await pick_option(page, fields.get_by_role("combobox", name="id 的输出字段", exact=True), "order_id")
    await fields.get_by_role("checkbox", name="同步字段 memo", exact=True).uncheck()
    await pick_option(page, fields.get_by_role("combobox", name="amount 的输出字段", exact=True), "order_id")
    await expect(fields.get_by_text("输出字段名称不能重复", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await pick_option(page, fields.get_by_role("combobox", name="amount 的输出字段", exact=True), "amount")
    await fields.get_by_role("button", name="上移 amount", exact=True).click()
    await fields.get_by_role("button", name="使用映射后的来源主键", exact=True).click()
    await fields.get_by_role("textbox", name="行过滤", exact=True).fill("amount > 0")
    await page.reload()
    await expect(fields.get_by_role("combobox", name="id 的输出字段", exact=True)).to_contain_text("order_id")
    await expect(fields.get_by_role("checkbox", name="同步字段 memo", exact=True)).not_to_be_checked()
    await expect(fields.get_by_role("textbox", name="输出主键", exact=True)).to_have_value("order_id")
    await expect(fields.get_by_role("textbox", name="行过滤", exact=True)).to_have_value("amount > 0")
    await fields.scroll_into_view_if_needed()
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "field-mapping.png"), full_page=True)
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    assert state["task"]["parameters"]["transform"] == [{
        "source-table": "app.orders", "projection": "`amount`, `id` AS `order_id`",
        "primary-keys": "order_id", "filter": "amount > 0",
    }]
    await page.reload()
    await expect(fields.get_by_role("combobox", name="id 的输出字段", exact=True)).to_contain_text("order_id")
    await fields.get_by_role("button", name="表达式", exact=True).click()
    projection = "id AS order_id, ABS(amount) AS amount"
    row_filter = "amount > 10 AND memo <> 'skip,me'"
    await fields.get_by_role("textbox", name="投影表达式", exact=True).fill(projection)
    await fields.get_by_role("textbox", name="行过滤", exact=True).fill(row_filter)
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    authored = deepcopy(state["task"]["parameters"])
    assert authored["transform"][0]["projection"] == projection
    assert authored["transform"][0]["filter"] == row_filter
    await page.reload()
    await expect(fields.get_by_role("textbox", name="投影表达式", exact=True)).to_have_value(projection)
    await expect(fields.get_by_role("textbox", name="行过滤", exact=True)).to_have_value(row_filter)
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Mapped orders renamed")
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    last_write = [request[2] for request in state["requests"]
                  if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/new-task"][-1]
    assert "parameters" not in last_write
    assert state["task"]["parameters"] == authored

    await pick_option(page, source_select, "orders_archive")
    confirm = page.get_by_role("dialog", name="更换来源表", exact=True)
    await expect(confirm).to_be_visible()
    await confirm.get_by_role("button", name="取消", exact=True).click()
    await expect(fields.get_by_role("textbox", name="投影表达式", exact=True)).to_have_value(projection)
    await pick_option(page, source_select, "orders_archive")
    await confirm.get_by_role("button", name="确认", exact=True).click()
    await expect(fields.get_by_role("textbox", name="行过滤", exact=True)).to_have_value("")
    await expect(fields.get_by_role("textbox", name="输出主键", exact=True)).to_have_value("")
    assert state["task"]["parameters"] == authored, "Unsaved reset must not modify the saved definition"


async def verify_snapshot_replication(page):
    """Bounded replication stays in its product family and histories retain their version."""
    from datetime import datetime, timezone
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_option(page, page.get_by_role("combobox", name="读取策略", exact=True), "全量后结束")
    await pick_datasources(page)
    for table in ("batch_a", "batch_b"):
        await page.get_by_role("checkbox", name="选择 app." + table, exact=True).check()
        await pick_option(page, page.get_by_role("combobox", name="去向表 app." + table, exact=True),
                          "ods.public." + table + "_copy")
    await expect(page.locator("#schedule")).to_be_visible()
    await expect(page.locator("#env").get_by_text("执行完成后结束", exact=True)).to_be_visible()
    await expect(page.get_by_role("switch", name="同步结构变更", exact=True)).to_have_count(0)
    await configure_cron(page, "0 0 8 * * ?")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    saved = deepcopy(state["task"]["parameters"])
    assert saved["source"]["type"] == "mysql"
    assert saved["source"]["startup"] == {"mode": "SNAPSHOT"}
    assert saved["pipeline"]["execution.runtime-mode"] == "BATCH"
    assert saved["pipeline"]["pipeline.table-scope"] == "DATABASE"
    assert len(saved["route"]) == 2 and saved["transform"] == []
    assert state["schedule"]["targetId"] == "new-task" and state["schedule"]["enabled"] is False
    await page.reload()
    await expect(page.get_by_role("combobox", name="读取策略", exact=True)).to_contain_text("全量后结束")
    await expect(page.locator("#basic").get_by_text("整库实时 · 全量后结束", exact=True)).to_be_visible()
    await expect(page.locator("#schedule")).to_be_visible()
    await page.get_by_role("button", name="保存并上线", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync$"))
    await pick_option(page, page.get_by_role("combobox").filter(has_text="全部同步类型"), "整库实时")
    await expect(page).to_have_url(re.compile(r"productType=DATABASE_REPLICATION"))
    await expect(page.get_by_text("整库实时 · 全量后结束", exact=True)).to_be_visible()
    request = [request[2] for request in state["requests"] if request[1] == "/api/v1/task-definitions/page"][-1]
    assert request["parameterFilters"]["dataSyncProfile"] == "DATABASE_REPLICATION"
    await expect(page.get_by_role("button", name="运行", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="启动", exact=True)).to_have_count(0)
    await page.goto(site("/data-sync/new-task/detail"))
    await expect(page.get_by_role("button", name="调度", exact=True)).to_be_visible()
    await page.get_by_role("button", name="运行", exact=True).click()
    await expect(page).to_have_url(re.compile(r"executionId=i1"))
    await expect(page.get_by_role("button", name="停止", exact=True)).to_be_visible()
    assert instance["definitionVersion"] == 1
    full = {"step": "FULL", "status": "FINISHED", "progressPercent": 100,
            "progressUnit": "SPLITS", "readRows": 4, "committedRows": 4,
            "plannedSplits": 1, "completedSplits": 1, "failureCode": None}
    report = {"version": 1, "observedAt": datetime.now(timezone.utc).isoformat(),
              "tables": [{"mappingIndex": index, **full} for index in range(2)]}
    instance.update({"status": "FINISHED", "progressPercent": 100,
                     "executionReport": json.dumps(report), "endTime": "2030-10-14T00:01:02"})
    await page.reload()
    await expect(page.get_by_role("heading", name="整库实时 · 全量后结束", exact=True)).to_be_visible()
    await expect(page.get_by_role("progressbar", name="离线同步", exact=True)).to_have_attribute("aria-valuenow", "100")
    await expect(page.get_by_role("status").filter(has_text="执行状态：")).to_contain_text("成功")
    await expect(page.get_by_role("button", name="停止", exact=True)).to_have_count(0)
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "snapshot-finished.png"), full_page=True)

    # Change only the current definition fixture; the selected instance is still version 1.
    current = deepcopy(saved)
    current["source"]["startup"] = {"mode": "INITIAL"}
    current["pipeline"]["execution.runtime-mode"] = "STREAMING"
    for table in current["sink"]["tables"]:
        table["write"] = {"mode": "UPSERT"}
    state["task"] = {**state["task"], "definitionVersion": 2, "parameters": current}
    history_schedule_id = "snapshot-history-schedule"
    instance.update({"triggerType": "SCHEDULE", "scheduleId": history_schedule_id,
                     "scheduleRevision": 3, "scheduledTime": "2030-10-14T00:01:00Z"})
    state["fires"] = [{"id": "snapshot-fire", "scheduleId": history_schedule_id,
                       "scheduleRevision": 3, "scheduledTime": instance["scheduledTime"],
                       "fireTime": "2030-10-14T00:01:01Z", "outcome": "ADMITTED",
                       "instanceId": instance["id"]}]
    current_schedule = deepcopy(state["schedule"])
    requests_before_history = len(state["requests"])
    await page.reload()
    await expect(page.get_by_text("整库实时 · 全量后持续增量", exact=True)).to_be_visible()
    await expect(page.get_by_role("heading", name="整库实时 · 全量后结束", exact=True)).to_be_visible()
    assert state["versions"][("new-task", 1)]["parameters"] == saved
    await expect(page.get_by_role("button", name="启动", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="调度", exact=True)).to_have_count(0)
    await expect(page.get_by_text("调度版本：3", exact=True)).to_be_visible()
    await page.get_by_role("button", name="触发记录", exact=True).click()
    history = page.get_by_role("dialog", name="触发记录", exact=True)
    await expect(history).to_be_visible()
    await expect(history.get_by_role("button", name="查看实例", exact=True)).to_be_visible()
    await expect(history.get_by_role("tab", name="配置", exact=True)).to_have_count(0)
    await expect(history.get_by_role("button", name="打开 Cron 调度配置", exact=True)).to_have_count(0)
    await expect(history.get_by_role("switch", name="启用调度", exact=True)).to_have_count(0)
    await expect(history.get_by_role("button", name="保存", exact=True)).to_have_count(0)
    await expect(history.get_by_role("button", name="删除调度", exact=True)).to_have_count(0)
    history_requests = [request for request in state["requests"][requests_before_history:]
                        if request[1].startswith("/api/v1/schedules")]
    assert history_requests and all(
        request[0] == "POST" and request[1] == f"/api/v1/schedules/{history_schedule_id}/fires/page"
        and request[3] == "ws1" for request in history_requests
    ), history_requests
    await history.get_by_role("button", name="关闭", exact=True).click()
    await expect(page.get_by_role("dialog")).to_have_count(0)
    await expect(page.get_by_role("button", name="调度", exact=True)).to_have_count(0)
    await expect(page.get_by_role("button", name="启动", exact=True)).to_be_visible()
    assert state["schedule"] == current_schedule
