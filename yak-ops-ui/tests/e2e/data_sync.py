"""UI contracts for sync solution selection and schedule-save recovery (mocked APIs)."""
import os
import re
from pathlib import Path
from playwright.async_api import expect
from mock_api import state

def site(path):
    return os.environ["YAK_UI_BASE_URL"].rstrip("/") + path

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
    if kind == "单表离线":
        await expect(dialog.get_by_role("checkbox", name="结构迁移", exact=True)).not_to_be_checked()
        await expect(dialog.get_by_role("checkbox", name="结构迁移", exact=True)).to_be_enabled()
        await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_checked()
    else:
        await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_checked()
        await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_enabled()
        await expect(dialog.get_by_role("checkbox", name="增量同步", exact=True)).to_be_checked()
        await expect(dialog.get_by_role("checkbox", name="增量同步", exact=True)).to_be_disabled()
    await dialog.get_by_role("button", name="下一步", exact=True).click()


async def verify_solution_creation(page):
    await page.goto(site("/data-sync"))
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
    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "单表实时", "CDC draft")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("CDC draft")
    await expect(page.locator("#schedule")).to_have_count(0)
    await expect(page.locator("#datasource")).to_be_visible()
    await expect(page.locator("#env")).to_be_visible()
    await expect(page.locator("#transform")).to_have_count(0)
    await expect(page.locator("#datasource").get_by_text("来源", exact=True)).to_be_visible()
    await expect(page.locator("#datasource").get_by_text("去向", exact=True)).to_be_visible()
    await expect(page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True)).to_have_count(2)
    await expect(page.locator("#env").get_by_role("switch", name="Debug")).to_be_visible()
    # Catch missing Field.Root context when the realtime editor first renders.
    schema_switch = page.get_by_role("switch", name="同步结构变更", exact=True)
    await expect(schema_switch).to_be_visible()
    await expect(schema_switch).to_be_checked()
    await schema_switch.click()
    await expect(schema_switch).not_to_be_checked()
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Restored draft")
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Restored draft")
    await expect(schema_switch).not_to_be_checked()
    await schema_switch.click()
    await expect(schema_switch).to_be_checked()
    await expect(page.locator("#target").get_by_role("combobox").last).to_be_disabled()
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "solution-editor-desktop.png"), full_page=True)
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
    await page.goto(site("/data-sync/new"))
    await choose_solution(page, "单表离线", "Partial save")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    assert state["created_tasks"] == 0
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Partial save")
    await pick_datasources(page)
    await page.locator("#source").get_by_role("combobox", name="表", exact=True).click()
    await page.get_by_role("option", name="orders", exact=True).click()
    await page.locator("#target").get_by_role("combobox", name="表", exact=True).click()
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
    """Database batch selection, duplicate guard, draft restoration and saved mapping."""
    import json
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库离线")
    await expect(dialog.get_by_role("checkbox", name="全量同步")).to_be_checked()
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
    await pick_option(page, batch_write_mode(page), "按主键更新")
    await page.reload()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    requests = [req[2] for req in state["requests"]
                if req[0] == "POST" and req[1] == "/api/v1/task-definitions"]
    assert len(requests) == 1, requests
    parameters = requests[0]["parameters"]
    assert parameters["source"]["type"] == "jdbc-mysql" and len(parameters["route"]) == 2, parameters
    assert [row["table"] for row in parameters["source"]["tables"]] == ["batch_a", "batch_b"]
    assert [row["table"] for row in parameters["sink"]["tables"]] == ["batch_a_copy", "batch_b_copy"]
    assert all(row["write"]["mode"] == "UPSERT" for row in parameters["sink"]["tables"])



async def verify_multitable_selection(page):
    """Filter source tables, choose several, remove a mapping, and save the remaining table."""
    import json

    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库离线")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
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
    assert [entry["table"] for entry in saved["source"]["tables"]] == ["batch_a"]
    assert [entry["table"] for entry in saved["sink"]["tables"]] == ["batch_a_copy"]


async def verify_schema_configuration(page):
    """Schema preparation remains configurable without exposing a speculative DDL preview."""
    import json
    state["catalog_mode"] = "schema"
    dialog = await prepare_database_task(page, "单表离线")
    schema = dialog.get_by_role("checkbox", name="结构迁移", exact=True)
    await expect(schema).not_to_be_checked()
    await expect(schema).to_be_enabled()
    await schema.check()
    await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_checked()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    await pick_option(page, page.locator("#source").get_by_role("combobox").last,
                      re.compile(r"(.*\.)?sm_browser_source$"))
    target = page.locator("#target")
    schema_select = target.get_by_role("combobox", name="Schema", exact=True)
    if await schema_select.count():
        await pick_option(page, schema_select, "public")
    field = target.get_by_placeholder("去向表名称", exact=True)
    await expect(field).to_be_enabled()
    await expect(page.get_by_role("button", name="DDL 预览", exact=True)).to_have_count(0)
    for table in ("sm_browser_unused", "sm_browser_target"):
        await field.fill(table)
    assert state["created_tasks"] == 0, "Editing schema configuration must not save a Task"
    assert not any(req[1] == "/api/v1/data-sync/schema/preview" for req in state["requests"])

    await page.reload()
    await expect(page.get_by_placeholder("去向表名称", exact=True)).to_have_value("sm_browser_target")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    created = [req[2] for req in state["requests"]
               if req[0] == "POST" and req[1] == "/api/v1/task-definitions"][-1]
    params = created["parameters"]
    assert params["source"]["syncMode"] == "FULL", params
    assert params["sink"]["schemaPolicy"]["initial"] == "CREATE_OR_ALTER"
    assert params["sink"]["tables"][0]["table"] == "sm_browser_target"


async def verify_realtime_stages(page):
    """Database realtime setup and progress display, without a live CDC consumer."""
    import json
    from datetime import datetime, timezone
    from mock_api import instance
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    for step in ("全量同步", "增量同步"):
        field = dialog.get_by_role("checkbox", name=step, exact=True)
        await expect(field).to_be_checked()
    await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_enabled()
    await expect(dialog.get_by_role("checkbox", name="增量同步", exact=True)).to_be_disabled()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
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
