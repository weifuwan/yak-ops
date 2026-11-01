"""UI contracts for sync solution selection and schedule-save recovery (mocked APIs)."""
import asyncio
from copy import deepcopy
import json
import os
import re
from pathlib import Path
from playwright.async_api import expect
from mock_api import definition_response, instance, mock, remember_definition, state

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


async def open_properties(page):
    await page.get_by_role("button", name="属性", exact=True).click()
    drawer = page.get_by_role("dialog", name="属性", exact=True)
    await expect(drawer).to_be_visible()
    return drawer


async def expect_task_name(page, name):
    drawer = await open_properties(page)
    await expect(drawer.get_by_role("textbox", name="任务名称", exact=True)).to_have_value(name)
    await drawer.get_by_role("button", name="取消", exact=True).click()
    await expect(drawer).to_be_hidden()


async def rename_task(page, name):
    drawer = await open_properties(page)
    await drawer.get_by_role("textbox", name="任务名称", exact=True).fill(name)
    await apply_drawer(drawer)


async def open_advanced(page, tab="高级参数", parameter_tab="读端配置"):
    drawer = page.get_by_role("dialog", name="高级配置", exact=True)
    if not await drawer.count():
        await page.get_by_role("button", name="高级配置", exact=True).click()
    await expect(drawer).to_be_visible()
    await drawer.get_by_role("tab", name=tab, exact=True).click()
    if tab == "高级参数":
        await drawer.get_by_role("tab", name=parameter_tab, exact=True).click()
    return drawer


async def apply_drawer(drawer):
    await drawer.get_by_role("button", name="确认", exact=True).click()
    await expect(drawer).to_be_hidden()


async def set_read_strategy(page, label):
    drawer = await open_advanced(page)
    await pick_option(page, drawer.get_by_role("combobox", name="读取策略", exact=True), label)
    await apply_drawer(drawer)


async def expect_read_strategy(page, label, disabled=False):
    drawer = await open_advanced(page)
    strategy = drawer.get_by_role("combobox", name="读取策略", exact=True)
    await expect(strategy).to_contain_text(label)
    if disabled:
        await expect(strategy).to_be_disabled()
    await drawer.get_by_role("button", name="取消", exact=True).click()
    await expect(drawer).to_be_hidden()


async def set_schema_changes(page, enabled):
    drawer = await open_advanced(page, "DDL 能力配置")
    toggle = drawer.get_by_role("switch", name="同步结构变更", exact=True)
    await toggle.set_checked(enabled)
    await apply_drawer(drawer)


async def configure_editor_cron(page, expression):
    drawer = await open_advanced(page, parameter_tab="运行时配置")
    await configure_cron(page, expression)
    await apply_drawer(drawer)


async def leave_editor(page):
    await page.get_by_role("button", name="任务列表", exact=True).click()
    guard = page.get_by_role("dialog", name="离开当前页面", exact=True)
    await expect(guard).to_be_visible()
    await guard.get_by_role("button", name="放弃修改并离开", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync$"))


async def expect_save_rejected(page, message):
    """Validation must reveal the offending field and must never submit invalid parameters."""
    before = (state["created_tasks"], state["updated_tasks"])
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page.get_by_text(message, exact=True).first).to_be_visible()
    assert (state["created_tasks"], state["updated_tasks"]) == before

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
    await expect_task_name(page, "CDC draft")
    await expect(page.locator("#field-mapping")).to_have_count(0)
    await expect(page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True)).to_have_count(2)
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#schedule")).to_have_count(0)
    await expect(advanced.locator("#env").get_by_text("持续运行", exact=True)).to_be_visible()
    await advanced.get_by_role("tab", name="DDL 能力配置", exact=True).click()
    schema_switch = advanced.get_by_role("switch", name="同步结构变更", exact=True)
    await expect(schema_switch).to_be_checked()
    await schema_switch.click()
    await expect(schema_switch).not_to_be_checked()
    await apply_drawer(advanced)
    await rename_task(page, "Restored draft")
    await page.reload()
    await expect_task_name(page, "Restored draft")
    advanced = await open_advanced(page, "DDL 能力配置")
    await expect(schema_switch).not_to_be_checked()
    await advanced.get_by_role("button", name="取消", exact=True).click()
    await set_read_strategy(page, "全量后结束")
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#schedule")).to_be_visible()
    await advanced.get_by_role("tab", name="DDL 能力配置", exact=True).click()
    await expect(schema_switch).to_have_count(0)
    await advanced.get_by_role("button", name="取消", exact=True).click()
    await page.reload()
    await expect_read_strategy(page, "全量后结束")
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "solution-editor-desktop.png"), full_page=True)

    # Re-selecting the same product restores the complete strategy-bearing draft.
    await leave_editor(page)
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await pick_option(page, dialog.get_by_role("combobox").nth(2), "单表离线")
    await pick_option(page, dialog.get_by_role("combobox").nth(2), "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await expect_read_strategy(page, "全量后结束")
    await expect_task_name(page, "Restored draft")
    await leave_editor(page)
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
    await expect_task_name(page, "Workspace B draft")
    await page.get_by_role("button", name=re.compile("Tester")).click()
    await page.get_by_role("menuitem", name="退出登录", exact=True).click()
    await page.get_by_role("dialog", name="离开当前页面", exact=True).get_by_role(
        "button", name="放弃修改并离开", exact=True
    ).click()
    await expect(page).to_have_url(re.compile(r"/login(?:\?|$)"))
    assert not await page.evaluate("Object.keys(sessionStorage).some(k => k.startsWith('yak.data-sync.draft:'))")
    assert [request for request in state["requests"] if request[1].endswith("/account/logout")][-1][3] is None


async def configure_cron(page, expression):
    await page.get_by_role("button", name="打开 Cron 调度配置", exact=True).click()
    await page.get_by_role("tab", name="高级 Cron", exact=True).click()
    await page.get_by_role("textbox", name="Cron 表达式", exact=True).fill(expression)
    await page.get_by_role("button", name="确认", exact=True).last.click()


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
    await expect_task_name(page, "Partial save")
    await pick_datasources(page)
    await pick_option(page, page.locator("#source").get_by_role("combobox", name="表", exact=True), "app.orders")
    await pick_option(page, page.locator("#target").get_by_role("combobox", name="表", exact=True), "ods.public.ods_orders")
    await configure_editor_cron(page, "0 0 6 * * ?")

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
        await expect(page.get_by_role("button", name="属性", exact=True)).to_be_disabled()
        await expect(page.get_by_role("button", name="高级配置", exact=True)).to_be_disabled()
        await expect(page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True).first).to_be_disabled()
    finally:
        gate.set()
    await expect(page).to_have_url(re.compile("/data-sync/new-task/edit$"))
    await page.unroute("**/api/v1/task-definitions", hold_creation)
    await expect(page.get_by_text(re.compile("任务已保存，调度未确认保存"))).to_be_visible()
    advanced = page.get_by_role("dialog", name="高级配置", exact=True)
    await expect(advanced.get_by_role("button", name="打开 Cron 调度配置", exact=True)).to_be_disabled()
    await expect(advanced.get_by_role("button", name="打开 Cron 调度配置", exact=True)).to_contain_text("0 0 6 * * ?")
    assert state["created_tasks"] == 1
    state["fail_schedule"] = False
    await advanced.get_by_role("button", name="重新加载状态", exact=True).click()
    await expect(advanced.get_by_role("button", name="打开 Cron 调度配置", exact=True)).to_be_enabled()
    await configure_cron(page, "0 0 7 * * ?")
    await apply_drawer(advanced)
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page.get_by_text("同步任务已保存", exact=True)).to_be_visible()
    assert state["created_tasks"] == 1 and state["updated_tasks"] == 0
    assert state["schedule"]["targetId"] == "new-task"
    assert state["schedule"]["cronExpression"] == "0 0 7 * * ?"
    assert state["schedule"]["enabled"] is False

    # The first creation's recovery state must not replay the failed Cron after a later save.
    await page.reload()
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.get_by_role("button", name="打开 Cron 调度配置", exact=True)).to_contain_text("0 0 7 * * ?")
    await expect(advanced.get_by_text(re.compile("任务已保存，调度未确认保存"))).to_have_count(0)
    await advanced.get_by_role("button", name="取消", exact=True).click()

    await close_notifications(page)
    state["fail_publish"] = True
    await page.get_by_role("button", name="发布", exact=True).click()
    await expect(page.get_by_text(re.compile("任务已保存，发布失败"))).to_be_visible()
    assert state["task"]["status"] == "UNPUBLISHED"
    assert state["created_tasks"] == 1 and state["updated_tasks"] == 0
    await expect_task_name(page, "Partial save")
    await close_notifications(page)
    state["fail_publish"] = False
    await page.get_by_role("button", name="发布", exact=True).click()
    await expect(page.get_by_text("本次发布 V1 已确认", exact=True)).to_be_visible()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    assert state["task"]["status"] == "PUBLISHED"
    assert state["task"]["publishedVersion"] == state["task"]["definitionVersion"]
    assert len(state["publications"]) == 1
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


def transfer_panel(page, title):
    return page.locator("#source section").filter(has=page.get_by_role("heading", name=title, exact=True))


def mapping_row(page, table):
    return page.locator("#target").get_by_role("row").filter(
        has=page.get_by_role("checkbox", name="选择映射 " + table, exact=True))


async def select_source_tables(page, tables):
    panel = transfer_panel(page, "源端库表")
    for table in tables:
        await panel.get_by_role("checkbox", name="选择 " + table, exact=True).check()
    await page.get_by_role("button", name="加入已选库表", exact=True).click()
    for table in tables:
        await expect(transfer_panel(page, "已选库表").get_by_role("checkbox", name="选择 " + table, exact=True)).to_be_visible()


async def expect_selected_tables(page, count):
    await expect(transfer_panel(page, "已选库表").get_by_role("checkbox", name=re.compile(r"^选择 "))).to_have_count(count)


async def open_mapping(page, table, tab="目标映射"):
    await mapping_row(page, table).get_by_role("button", name="配置", exact=True).last.click()
    dialog = page.get_by_role("dialog", name="目标表配置", exact=True)
    await expect(dialog).to_be_visible()
    await expect(dialog.get_by_text(table, exact=True)).to_be_visible()
    await dialog.get_by_role("tab", name=tab, exact=True).click()
    return dialog


async def set_mapping_target(page, source, target):
    dialog = await open_mapping(page, source)
    await pick_option(page, dialog.get_by_role("combobox", name="目标表", exact=True), target)
    await apply_drawer(dialog)


async def expect_mapping_write_mode(page, table, label, disabled=False):
    dialog = await open_mapping(page, table)
    control = dialog.get_by_role("combobox", name="写入方式", exact=True)
    await expect(control).to_contain_text(label)
    if disabled:
        await expect(control).to_be_disabled()
    await dialog.get_by_role("button", name="取消", exact=True).click()


async def open_field_expressions(page):
    await page.locator("#field-mapping").get_by_role("button", name="表达式", exact=True).click()
    dialog = page.get_by_role("dialog", name="字段表达式配置", exact=True)
    await expect(dialog).to_be_visible()
    return dialog


async def capture_editor_widths(page, name, replication=False):
    artifacts = Path(os.environ["YAK_UI_ARTIFACT_DIR"])
    for width, height in ((1440, 900), (1920, 1080)):
        await page.set_viewport_size({"width": width, "height": height})
        await page.locator("#datasource").scroll_into_view_if_needed()
        await page.screenshot(path=str(artifacts / f"{name}-{width}.png"), full_page=True)
        anchor = "目标表映射" if replication else "数据去向"
        await page.get_by_role("navigation", name="任务配置导航").get_by_role("button", name=anchor, exact=True).click()
        await page.locator("#target").scroll_into_view_if_needed()
        if replication:
            await page.locator("#target table").evaluate("table => { table.parentElement.scrollLeft = table.parentElement.scrollWidth; }")
        await page.screenshot(path=str(artifacts / f"{name}-target-{width}.png"), full_page=True)
        if replication:
            await page.locator("#target table").evaluate("table => { table.parentElement.scrollLeft = 0; }")
    await page.set_viewport_size({"width": 1440, "height": 900})


async def verify_batch_table_options(page):
    """Author read/write intent and preview exact values without pretending to execute JDBC."""
    from mock_api import sources
    state["data_sources"] = [*sources, {
        "id": "mysql-dst", "name": "MySQL target", "dbType": "MYSQL", "database": "ods",
    }]
    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "单表离线", "Batch table options", target_type="MySQL")
    for index, label in enumerate(("MySQL source", "MySQL target")):
        await pick_option(page, page.locator("#datasource").get_by_role("combobox", name="数据源", exact=True).nth(index), label)
    source, target = page.locator("#source"), page.locator("#target")
    await pick_option(page, source.get_by_role("combobox", name="表", exact=True), "app.orders")
    await pick_option(page, target.get_by_role("combobox", name="表", exact=True), "ods.ods_orders")
    predicate = "id >= 1 OR id = 0"
    await source.get_by_role("textbox", name="数据过滤", exact=True).fill(predicate)
    await source.get_by_role("textbox", name="切分键", exact=True).fill("id")
    await source.get_by_role("button", name="来源高级配置", exact=True).click()
    await source.get_by_role("spinbutton", name="读取批次大小", exact=True).fill("128")
    await source.get_by_role("spinbutton", name="查询超时（秒）", exact=True).fill("30")

    state["fail_preview"] = True
    await source.get_by_role("button", name="数据预览", exact=True).click()
    preview = page.get_by_role("dialog", name="数据预览", exact=True)
    await expect(preview.get_by_role("alert")).to_contain_text("数据预览失败")
    state["fail_preview"] = False
    await preview.get_by_role("button", name="重试", exact=True).click()
    await expect(preview.get_by_text("9007199254740993", exact=True)).to_be_visible()
    await expect(preview.get_by_text("123456789012345.678900", exact=True)).to_be_visible()
    requests = [request for request in state["requests"] if request[1].endswith("/catalog/preview")]
    assert len(requests) == 2 and all(request[3] == "ws1" for request in requests)
    assert requests[-1][2] == {"database": "app", "table": "orders", "where": predicate, "limit": 20}
    assert state["created_tasks"] == state["updated_tasks"] == 0
    await preview.get_by_role("button", name="关闭", exact=True).click()

    await expect(target.get_by_role("combobox", name="主键冲突", exact=True)).to_contain_text("insert into（主键冲突时报错）")
    await pick_option(page, target.get_by_role("combobox", name="主键冲突", exact=True), "replace into（替换冲突记录）")
    await target.get_by_role("textbox", name="导入前准备语句 1", exact=True).fill("DELETE FROM ods_orders")
    await target.get_by_role("button", name="添加", exact=True).first.click()
    await target.get_by_role("textbox", name="导入前准备语句 2", exact=True).fill("INSERT INTO ods_orders (id) VALUES (0)")
    await target.get_by_role("textbox", name="导入后完成语句 1", exact=True).fill("DELETE FROM ods_orders WHERE id = 0")
    await target.get_by_role("button", name="去向高级配置", exact=True).click()
    await target.get_by_role("spinbutton", name="写入批次大小", exact=True).fill("64")
    await page.get_by_role("button", name="编辑字段类型映射", exact=True).click()
    types = page.get_by_role("dialog", name="编辑字段类型映射", exact=True)
    await types.get_by_role("button", name="添加类型映射", exact=True).click()
    await pick_option(page, types.get_by_role("combobox", name="来源类型 1", exact=True), "INT")
    await pick_option(page, types.get_by_role("combobox", name="目标类型 1", exact=True), "BIGINT")
    await apply_drawer(types)
    assert state["created_tasks"] == 0
    await page.reload()
    await expect(source.get_by_role("textbox", name="数据过滤", exact=True)).to_have_value(predicate)
    await expect(source.get_by_role("textbox", name="切分键", exact=True)).to_have_value("id")
    await expect(target.get_by_role("textbox", name="导入前准备语句 2", exact=True)).to_have_value("INSERT INTO ods_orders (id) VALUES (0)")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    saved = deepcopy(state["task"]["parameters"])
    assert state["task"]["parameterSchemaVersion"] == 8
    assert saved["source"]["tables"][0]["read"] == {"where": predicate, "splitKey": "id"}
    assert saved["source"]["tables"][0]["options"] == {"fetchSize": 128, "queryTimeoutSeconds": 30}
    assert saved["sink"]["tables"][0]["write"] == {
        "mode": "REPLACE", "batchSize": 64,
        "preSql": ["DELETE FROM ods_orders", "INSERT INTO ods_orders (id) VALUES (0)"],
        "postSql": ["DELETE FROM ods_orders WHERE id = 0"],
    }
    assert saved["sink"]["typeMappings"] == [{"sourceType": "INT", "targetType": "BIGINT"}]
    await page.reload()
    await expect(target.get_by_role("combobox", name="主键冲突", exact=True)).to_contain_text("replace into（替换冲突记录）")
    await source.get_by_role("button", name="来源高级配置", exact=True).click()
    await expect(source.get_by_role("spinbutton", name="读取批次大小", exact=True)).to_have_value("128")
    await expect(source.get_by_role("spinbutton", name="查询超时（秒）", exact=True)).to_have_value("30")
    await capture_editor_widths(page, "batch-table-options")
    assert state["task"]["parameters"] == saved and state["updated_tasks"] == 0


async def verify_replication_table_options(page):
    """Per-table edits, selective bulk changes and serialized dynamic-selection intent."""
    state["catalog_mode"] = "multi"
    state["catalog_page_size"] = 1
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    await expect(page.get_by_role("radio", name="使用正则选表", exact=True)).to_be_disabled()
    advanced = await open_advanced(page, "DDL 能力配置")
    await pick_option(page, advanced.get_by_role("combobox", name="目标表结构", exact=True), "创建或调整目标表")
    await apply_drawer(advanced)
    await transfer_panel(page, "源端库表").get_by_role("button", name="加载更多", exact=True).click()
    await select_source_tables(page, ["app.batch_a", "app.batch_b"])
    await expect_selected_tables(page, 2)
    pages = [request[2] for request in state["requests"] if request[1] == "/api/v1/data-source/src/catalog/table-page"]
    assert {request["offset"] for request in pages} >= {0, 1}
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await mapping.get_by_role("textbox", name="全量条件", exact=True).fill("id > 99")
    await mapping.get_by_role("button", name="取消", exact=True).click()
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await expect(mapping.get_by_role("textbox", name="全量条件", exact=True)).to_have_value("")
    await mapping.get_by_role("textbox", name="源端切分列", exact=True).fill("id")
    await mapping.get_by_role("textbox", name="全量条件", exact=True).fill("id > 0")
    await mapping.get_by_role("checkbox", name="DELETE", exact=True).uncheck()
    await mapping.get_by_role("tab", name="引擎参数", exact=True).click()
    await mapping.get_by_role("spinbutton", name="写入批次大小", exact=True).fill("64")
    await apply_drawer(mapping)
    mapping = await open_mapping(page, "app.batch_b", "同步配置")
    await mapping.get_by_role("checkbox", name="是否跳过全量同步", exact=True).check()
    await apply_drawer(mapping)
    for table in ("app.batch_a", "app.batch_b"):
        await mapping_row(page, table).get_by_role("checkbox", name="选择映射 " + table, exact=True).check()
    await page.locator("#target").get_by_role("button", name="批量修改", exact=True).click()
    bulk = page.get_by_role("dialog", name="批量修改 2 张表", exact=True)
    await bulk.get_by_role("checkbox", name="目标表模板", exact=True).check()
    await bulk.get_by_role("textbox", name="目标表模板", exact=True).fill("dw_${table}")
    await bulk.get_by_role("checkbox", name="写入批次大小", exact=True).check()
    await bulk.get_by_role("spinbutton", name="写入批次大小", exact=True).fill("128")
    await apply_drawer(bulk)
    assert state["created_tasks"] == state["updated_tasks"] == 0
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    saved = deepcopy(state["task"]["parameters"])
    assert saved["source"]["tables"][0]["snapshot"] == {"chunkKeyColumn": "id", "where": "id > 0"}
    assert saved["source"]["tables"][0]["dml"] == {"events": ["INSERT", "UPDATE"]}
    assert saved["source"]["tables"][1]["snapshot"] == {"skip": True}
    assert "dml" not in saved["source"]["tables"][1]
    assert [table["table"] for table in saved["sink"]["tables"]] == ["dw_batch_a", "dw_batch_b"]
    assert all(table["write"]["batchSize"] == 128 for table in saved["sink"]["tables"])
    await page.reload()
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await expect(mapping.get_by_role("textbox", name="全量条件", exact=True)).to_have_value("id > 0")
    await expect(mapping.get_by_role("checkbox", name="DELETE", exact=True)).not_to_be_checked()
    await mapping.get_by_role("button", name="取消", exact=True).click()
    await capture_editor_widths(page, "replication-explicit", replication=True)
    await page.get_by_role("radio", name="使用正则选表", exact=True).click()
    change = page.get_by_role("dialog", name="切换选表方式", exact=True)
    await change.get_by_role("button", name="取消", exact=True).click()
    await expect(page.get_by_role("radio", name="使用正则选表", exact=True)).not_to_be_checked()
    await expect_selected_tables(page, 2)
    await page.get_by_role("radio", name="使用正则选表", exact=True).click()
    await change.get_by_role("button", name="确认", exact=True).click()
    await page.get_by_role("textbox", name="库名正则", exact=True).fill("^app$")
    await page.get_by_role("textbox", name="表名正则", exact=True).fill("^batch_[ab]$")
    await page.get_by_role("textbox", name="新表目标表模板", exact=True).fill("regex_${table}")
    await page.get_by_role("button", name="新表默认配置", exact=True).click()
    await page.locator("#source").get_by_role("textbox", name="全量条件", exact=True).fill("id > 5")
    await page.locator("#source").get_by_role("checkbox", name="DELETE", exact=True).uncheck()
    await expect_save_rejected(page, "请先匹配并应用当前正则规则")
    await page.get_by_role("button", name="匹配库表", exact=True).click()
    await expect(page.get_by_text("已匹配 2 张表", exact=True)).to_be_visible()
    assert state["task"]["parameters"] == saved
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    selected = state["task"]["parameters"]
    assert selected["source"]["selection"] == {
        "mode": "REGEX", "databasePattern": "^app$", "tablePattern": "^batch_[ab]$",
        "snapshot": {"where": "id > 5"}, "dml": {"events": ["INSERT", "UPDATE"]},
    }
    assert selected["sink"]["mapping"]["table"] == "regex_${table}"
    assert [table["table"] for table in selected["sink"]["tables"]] == ["regex_batch_a", "regex_batch_b"]
    assert state["task"]["definitionVersion"] == 2 and state["task"]["parameterSchemaVersion"] == 8
    assert not state["publications"], "Editing selection must not publish or run a CDC consumer"
    await page.reload()
    await expect(page.get_by_role("radio", name="使用正则选表", exact=True)).to_be_checked()
    await expect(page.get_by_role("textbox", name="新表目标表模板", exact=True)).to_have_value("regex_${table}")
    await capture_editor_widths(page, "replication-table-options", replication=True)

    # A delayed rematch must not let a dialog edit a row that its response can remove.
    persisted = deepcopy(state["task"]["parameters"])
    writes = (state["created_tasks"], state["updated_tasks"])
    await page.get_by_role("textbox", name="表名正则", exact=True).fill("^batch_a$")
    state["table_page_gate"] = asyncio.Event()
    state["table_page_started"] = asyncio.Event()
    await page.get_by_role("button", name="匹配库表", exact=True).click()
    try:
        await asyncio.wait_for(state["table_page_started"].wait(), timeout=5)
        for table in ("app.batch_a", "app.batch_b"):
            await expect(mapping_row(page, table).get_by_role("button", name="配置", exact=True).last).to_be_disabled()
        await expect(page.get_by_role("button", name="匹配库表", exact=True)).to_be_disabled()
        assert (state["created_tasks"], state["updated_tasks"]) == writes
        assert state["task"]["parameters"] == persisted
    finally:
        state.pop("table_page_gate").set()
        state.pop("table_page_started")
    await expect(page.get_by_text("已匹配 1 张表", exact=True)).to_be_visible()
    await expect(mapping_row(page, "app.batch_b")).to_have_count(0)
    await expect(mapping_row(page, "app.batch_a").get_by_role("button", name="配置", exact=True).last).to_be_enabled()
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await expect(page.get_by_role("button", name="匹配库表", exact=True, include_hidden=True)).to_be_disabled()
    await mapping.get_by_role("textbox", name="全量条件", exact=True).fill("id > 99")
    await mapping.get_by_role("button", name="取消", exact=True).click()
    await expect(page.get_by_role("button", name="匹配库表", exact=True)).to_be_enabled()
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await expect(mapping.get_by_role("textbox", name="全量条件", exact=True)).to_have_value("id > 5")
    await mapping.get_by_role("textbox", name="全量条件", exact=True).fill("id > 6")
    await apply_drawer(mapping)
    assert state["task"]["parameters"] == persisted
    assert (state["created_tasks"], state["updated_tasks"]) == writes
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    final = state["task"]["parameters"]
    assert final["source"]["selection"]["tablePattern"] == "^batch_a$"
    assert final["source"]["selection"]["snapshot"] == {"where": "id > 5"}
    assert len(final["source"]["tables"]) == 1
    assert final["source"]["tables"][0]["snapshot"] == {"where": "id > 6"}
    await page.reload()
    mapping = await open_mapping(page, "app.batch_a", "同步配置")
    await expect(mapping.get_by_role("textbox", name="全量条件", exact=True)).to_have_value("id > 6")
    await mapping.get_by_role("button", name="取消", exact=True).click()


async def verify_multitable_mapping(page):
    """Replication selection, duplicate guard, draft restoration and saved mapping."""
    import json
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    await select_source_tables(page, ["app.batch_a", "app.batch_b"])
    for table in ("batch_a", "batch_b"):
        await set_mapping_target(page, "app." + table, "ods.public.batch_a_copy")
    await expect(mapping_row(page, "app.batch_a").get_by_text("目标冲突", exact=True)).to_be_visible()
    await expect(mapping_row(page, "app.batch_b").get_by_text("目标冲突", exact=True)).to_be_visible()
    await expect_save_rejected(page, "请完善目标表和表映射")
    await set_mapping_target(page, "app.batch_b", "ods.public.batch_b_copy")
    await expect_mapping_write_mode(page, "app.batch_a", "按主键更新", disabled=True)
    await page.reload()
    await expect_selected_tables(page, 2)
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
    await set_read_strategy(page, "全量后结束")
    await pick_datasources(page)

    source = transfer_panel(page, "源端库表")
    search = source.get_by_role("textbox", name="源端库表表过滤", exact=True)
    checkbox_a = source.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?batch_a$"))
    checkbox_b = source.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?batch_b$"))

    await search.fill("batch_b")
    await search.press("Enter")
    await expect(checkbox_a).to_have_count(0)
    await source.get_by_role("checkbox").first.check()
    await expect(checkbox_b).to_be_checked()
    await page.get_by_role("button", name="加入已选库表", exact=True).click()
    await expect_selected_tables(page, 1)

    await search.fill("")
    await search.press("Enter")
    await select_source_tables(page, ["app.batch_a"])
    await expect_selected_tables(page, 2)

    await transfer_panel(page, "已选库表").get_by_role("checkbox", name="选择 app.batch_b", exact=True).check()
    await page.get_by_role("button", name="移除已选库表", exact=True).click()
    await page.get_by_role("dialog", name="移除已选库表", exact=True).get_by_role("button", name="确认", exact=True).click()
    await expect_selected_tables(page, 1)
    await select_source_tables(page, ["app.batch_b"])
    await expect_selected_tables(page, 2)

    await mapping_row(page, "app.batch_b").get_by_role("checkbox", name="选择映射 app.batch_b", exact=True).check()
    await page.locator("#target").get_by_role("button", name="批量移除", exact=True).click()
    await page.get_by_role("dialog", name="批量移除库表", exact=True).get_by_role("button", name="确认", exact=True).click()
    await expect(checkbox_b).not_to_be_checked()
    await expect_selected_tables(page, 1)

    await set_mapping_target(page, "app.batch_a", "ods.public.batch_a_copy")
    await page.reload()
    await expect_selected_tables(page, 1)
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
    advanced = await open_advanced(page, "DDL 能力配置")
    await expect(advanced.get_by_role("combobox", name="目标表结构", exact=True)).to_contain_text("校验已有表")
    await pick_option(page, advanced.get_by_role("combobox", name="目标表结构", exact=True), "创建或调整目标表")
    await apply_drawer(advanced)
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
    advanced = await open_advanced(page, "DDL 能力配置")
    await expect(advanced.get_by_role("combobox", name="目标表结构", exact=True)).to_contain_text("创建或调整目标表")
    await advanced.get_by_role("button", name="取消", exact=True).click()
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
    await expect_read_strategy(page, "全量后持续增量")
    await pick_datasources(page)
    await select_source_tables(page, ["app.batch_a", "app.batch_b"])
    for table in ("batch_a", "batch_b"):
        await set_mapping_target(page, "app." + table, "ods.public." + table + "_copy")
    await expect_mapping_write_mode(page, "app.batch_a", "按主键更新", disabled=True)
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#schedule")).to_have_count(0)
    await advanced.get_by_role("button", name="取消", exact=True).click()
    await page.reload()
    await expect_selected_tables(page, 2)
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
    state["task"] = definition_response({
        **state["task"], "status": "PUBLISHED",
        "publishedVersion": state["task"]["definitionVersion"],
        "publishedDebug": state["task"]["debug"],
        "revision": state["task"]["revision"] + 1,
    })
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
    state["task"] = definition_response({
        **task, "status": "UNPUBLISHED", "parameters": config,
        "publishedVersion": None, "publishedDebug": None,
    })
    await page.goto(site("/data-sync/task1/edit"))
    await expect_task_name(page, task["name"])
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#env").get_by_text("持续运行", exact=True)).to_be_visible()
    await advanced.get_by_role("button", name="取消", exact=True).click()
    assert any("/src/catalog/columns" in request[1] for request in state["requests"])
    await rename_task(page, "Renamed replication")
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    metadata_request = [request[2] for request in state["requests"]
                        if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/task1"][-1]
    assert "parameters" not in metadata_request
    assert metadata_request["expectedRevision"] == 1 and state["task"]["revision"] == 2
    assert state["task"]["parameters"] == config
    assert state["task"]["definitionVersion"] == 1

    advanced = await open_advanced(page, "DDL 能力配置")
    schema_switch = advanced.get_by_role("switch", name="同步结构变更", exact=True)
    await expect(schema_switch).to_be_checked()
    await advanced.get_by_role("button", name="取消", exact=True).click()
    for enabled in (False, True):
        await set_schema_changes(page, enabled)
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
        await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
        advanced = await open_advanced(page, "DDL 能力配置")
        if enabled:
            await expect(schema_switch).to_be_checked()
        else:
            await expect(schema_switch).not_to_be_checked()
        await advanced.get_by_role("button", name="取消", exact=True).click()

    positioned = deepcopy(state["task"]["parameters"])
    positioned["source"]["startup"] = {
        "mode": "SPECIFIC_OFFSETS", "file": "mysql-bin.000007", "pos": 456,
        "gtidSet": "24bc7850-2c16-11e6-a073-0242ac110002:1-12", "skipEvents": 2, "skipRows": 1,
    }
    state["task"] = {**state["task"], "parameters": positioned,
                     "definitionVersion": state["task"]["definitionVersion"] + 1,
                     "revision": state["task"]["revision"] + 1}
    await page.reload()
    await expect_read_strategy(page, "仅增量")
    await set_read_strategy(page, "仅增量")
    await set_schema_changes(page, False)
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
    await select_source_tables(page, ["app.orders"])
    await set_mapping_target(page, "app.orders", "ods.ods_orders")
    await expect_save_rejected(page, "无主键来源表需关闭同步结构变更")
    await set_schema_changes(page, False)
    mapping = await open_mapping(page, "app.orders", "同步配置")
    await mapping.get_by_role("textbox", name="源端切分列", exact=True).fill("bucket")
    # An explicit change of write identity must not retain hidden target-key overrides.
    await mapping.get_by_role("textbox", name="目标表同步主键", exact=True).fill("bucket")
    await mapping.get_by_role("tab", name="目标映射", exact=True).click()
    writer = mapping.get_by_role("combobox", name="写入方式", exact=True)
    await pick_option(page, writer, "逐行更新")
    await mapping.get_by_role("tab", name="同步配置", exact=True).click()
    await expect(mapping.get_by_role("textbox", name="目标表同步主键", exact=True)).to_be_disabled()
    await expect(mapping.get_by_role("textbox", name="目标表同步主键", exact=True)).to_have_value("")
    await apply_drawer(mapping)
    await expect(transfer_panel(page, "源端库表").get_by_role("checkbox", name="选择 app.other_orders", exact=True)).to_be_disabled()
    await expect_read_strategy(page, "全量后持续增量", disabled=True)
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
    mapping = await open_mapping(page, "app.orders")
    await expect(writer).to_contain_text("逐行更新")
    await mapping.get_by_role("tab", name="同步配置", exact=True).click()
    await expect(mapping.get_by_role("textbox", name="源端切分列", exact=True)).to_have_value("bucket")
    await mapping.get_by_role("button", name="取消", exact=True).click()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()


async def verify_version_publication(page):
    """Drawer drafts, immutable history, explicit publication and route protection via real UI."""
    from mock_api import params, task

    original = deepcopy(params)
    original["pipeline"]["pipeline.table-scope"] = "SINGLE"
    state["task"] = definition_response({**task, "parameters": original})
    historical_instance = deepcopy(instance)
    await page.goto(site("/data-sync/task1/edit"))
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await expect(page.get_by_role("button", name="发布", exact=True)).to_be_disabled()
    await expect(page.get_by_role("button", name="转为脚本", exact=True)).to_have_count(0)

    # Cancel is local to a drawer, and applying a drawer still does not save the task.
    properties = await open_properties(page)
    await properties.get_by_role("textbox", name="任务名称", exact=True).fill("Canceled name")
    await properties.get_by_role("button", name="取消", exact=True).click()
    await expect_task_name(page, task["name"])
    assert state["updated_tasks"] == 0
    await rename_task(page, "Versioned orders")
    assert state["updated_tasks"] == 0 and state["task"]["name"] == task["name"]
    await page.get_by_role("button", name="任务列表", exact=True).click()
    guard = page.get_by_role("dialog", name="离开当前页面", exact=True)
    await expect(guard).to_be_visible()
    await guard.get_by_role("button", name="继续编辑", exact=True).click()
    await expect_task_name(page, "Versioned orders")
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    assert state["task"]["definitionVersion"] == 1 and state["task"]["revision"] == 2
    assert state["task"]["parameterSchemaVersion"] == 7, "Metadata-only saves preserve the historical parameter contract"

    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await advanced.get_by_role("textbox", name="并行度", exact=True).fill("9")
    await advanced.get_by_role("button", name="取消", exact=True).click()
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.get_by_role("textbox", name="并行度", exact=True)).to_have_value("")
    await advanced.get_by_role("textbox", name="并行度", exact=True).fill("4")
    await advanced.get_by_role("switch", name="Debug", exact=True).click()
    await expect(advanced.get_by_role("switch", name="Debug", exact=True)).to_be_checked()
    await apply_drawer(advanced)
    assert state["task"]["definitionVersion"] == 1 and not state["task"]["debug"]
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    assert state["task"]["status"] == "PUBLISHED"
    assert state["task"]["definitionVersion"] == 2 and state["task"]["publishedVersion"] == 1
    assert state["task"]["parameterSchemaVersion"] == 8
    assert state["task"]["debug"] and state["task"]["publishedDebug"] is False
    assert state["task"]["hasUnpublishedChanges"] and not state["publications"]
    assert instance == historical_instance
    saved_v2 = deepcopy(state["task"])

    await page.get_by_role("button", name="版本", exact=True).click()
    versions = page.get_by_role("dialog", name="版本", exact=True)
    old_row = versions.locator("tbody tr").filter(has=page.get_by_text("V1", exact=True))
    await old_row.get_by_role("button", name="查看", exact=True).click()
    snapshot = versions.locator('pre[aria-label="参数版本 V1"]')
    await expect(snapshot).to_be_visible()
    assert json.loads(await snapshot.inner_text()) == original
    await expect(page).to_have_url(re.compile(r"[?&]version=1(?:&|$)"))
    await old_row.get_by_role("button", name="对比", exact=True).click()
    await expect(versions.get_by_role("heading", name="V1 → V2", exact=True)).to_be_visible()
    await expect(versions.get_by_text("引擎 / 并行度", exact=True)).to_be_visible()
    await expect(guard).to_have_count(0)
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "version-comparison.png"), full_page=True)
    await versions.get_by_role("button", name="还原到当前编辑", exact=True).click()
    restore = page.get_by_role("dialog", name="还原参数版本 V1", exact=True)
    await expect(restore).to_be_visible()
    await restore.get_by_role("button", name="确认", exact=True).click()
    await expect(restore).to_be_hidden()
    assert state["task"] == saved_v2, "Restoring history must not save or publish automatically"
    assert state["versions"][("task1", 1)]["parameters"] == original
    assert state["versions"][("task1", 1)]["parameterSchemaVersion"] == 7
    await expect_task_name(page, "Versioned orders")
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.get_by_role("switch", name="Debug", exact=True)).to_be_checked()
    await expect(advanced.get_by_role("textbox", name="并行度", exact=True)).to_have_value("")
    await advanced.get_by_role("button", name="取消", exact=True).click()

    # History query changes must remain usable while the restored page has unsaved parameters.
    await page.get_by_role("button", name="版本", exact=True).click()
    current_row = versions.locator("tbody tr").filter(has=page.get_by_text("V2", exact=True))
    await current_row.get_by_role("button", name="查看", exact=True).click()
    await expect(versions.locator('pre[aria-label="参数版本 V2"]')).to_be_visible()
    await versions.get_by_role("tab", name="发布记录", exact=True).click()
    await expect(versions.get_by_text("暂无发布记录", exact=True)).to_be_visible()
    await expect(guard).to_have_count(0)
    await versions.get_by_role("button", name="关闭", exact=True).click()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    assert state["task"]["definitionVersion"] == 3 and state["task"]["parameters"] == original
    assert state["task"]["parameterSchemaVersion"] == 8, "Saving restored history creates a current-contract version"
    assert state["task"]["publishedVersion"] == 1 and state["task"]["publishedDebug"] is False
    assert state["versions"][("task1", 2)]["parameters"] == saved_v2["parameters"]
    async with page.expect_response(lambda response: response.request.method == "POST"
                                    and response.url.endswith("/api/v1/task-definitions/task1/publish")):
        await page.get_by_role("button", name="发布", exact=True).click()
    await expect(page.get_by_role("button", name="发布", exact=True)).to_be_disabled()
    assert state["task"]["publishedVersion"] == 3 and state["task"]["publishedDebug"] is True
    assert not state["task"]["hasUnpublishedChanges"] and len(state["publications"]) == 1
    assert instance == historical_instance

    # Debug-only saves have their own revision/publication without inventing a parameter version.
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await advanced.get_by_role("switch", name="Debug", exact=True).click()
    await apply_drawer(advanced)
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/task1")):
        await page.get_by_role("button", name="保存", exact=True).click()
    last_write = [request[2] for request in state["requests"]
                  if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/task1"][-1]
    assert "parameters" not in last_write
    assert state["task"]["definitionVersion"] == 3 and state["task"]["publishedDebug"] is True
    async with page.expect_response(lambda response: response.request.method == "POST"
                                    and response.url.endswith("/api/v1/task-definitions/task1/publish")):
        await page.get_by_role("button", name="发布", exact=True).click()
    await expect(page.get_by_role("button", name="发布", exact=True)).to_be_disabled()
    assert state["task"]["publishedDebug"] is False and len(state["publications"]) == 2
    assert len(state["versions"]) == 3 and instance == historical_instance
    await page.get_by_role("button", name="版本", exact=True).click()
    await versions.get_by_role("tab", name="发布记录", exact=True).click()
    await expect(versions.locator("tbody tr")).to_have_count(2)
    await expect(versions.get_by_role("cell", name="开启", exact=True)).to_be_visible()
    await expect(versions.get_by_role("cell", name="关闭", exact=True)).to_be_visible()
    await versions.get_by_role("button", name="关闭", exact=True).click()
    requests = [request[2] for request in state["requests"] if request[1].endswith("/publish")]
    assert len({request["requestId"] for request in requests}) == 2
    assert all(request["version"] == 3 and request["expectedRevision"] >= 1 for request in requests)

    saved = deepcopy(state["task"])
    await rename_task(page, "Unsaved title")
    await leave_editor(page)
    assert state["task"] == saved


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
    await pick_option(page, source_select, "app.orders")
    await pick_option(page, page.locator("#target").get_by_role("combobox", name="表", exact=True), "ods.public.ods_orders")
    fields = page.locator("#field-mapping")
    await expect(fields.get_by_role("checkbox", name="同步字段 id", exact=True)).to_be_checked()
    await pick_option(page, fields.get_by_role("combobox", name="id 的输出字段", exact=True), "order_id")
    await fields.get_by_role("checkbox", name="同步字段 memo", exact=True).uncheck()
    await pick_option(page, fields.get_by_role("combobox", name="amount 的输出字段", exact=True), "order_id")
    await expect(fields.get_by_text("输出字段名称不能重复", exact=True)).to_be_visible()
    await expect_save_rejected(page, "输出字段名称不能重复")
    await pick_option(page, fields.get_by_role("combobox", name="amount 的输出字段", exact=True), "amount")
    await fields.get_by_role("button", name="上移 amount", exact=True).click()
    await fields.get_by_role("button", name="使用映射后的来源主键", exact=True).click()
    expressions = await open_field_expressions(page)
    await expressions.get_by_role("textbox", name="行过滤", exact=True).fill("amount > 0")
    await apply_drawer(expressions)
    await page.reload()
    await expect(fields.get_by_role("combobox", name="id 的输出字段", exact=True)).to_contain_text("order_id")
    await expect(fields.get_by_role("checkbox", name="同步字段 memo", exact=True)).not_to_be_checked()
    expressions = await open_field_expressions(page)
    await expect(expressions.get_by_role("textbox", name="输出主键", exact=True)).to_have_value("order_id")
    await expect(expressions.get_by_role("textbox", name="行过滤", exact=True)).to_have_value("amount > 0")
    await expressions.get_by_role("button", name="取消", exact=True).click()
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
    expressions = await open_field_expressions(page)
    projection = "id AS order_id, ABS(amount) AS amount"
    row_filter = "amount > 10 AND memo <> 'skip,me'"
    await expressions.get_by_role("textbox", name="投影表达式", exact=True).fill(projection)
    await expressions.get_by_role("textbox", name="行过滤", exact=True).fill(row_filter)
    await apply_drawer(expressions)
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    authored = deepcopy(state["task"]["parameters"])
    assert authored["transform"][0]["projection"] == projection
    assert authored["transform"][0]["filter"] == row_filter
    await page.reload()
    expressions = await open_field_expressions(page)
    await expect(expressions.get_by_role("textbox", name="投影表达式", exact=True)).to_have_value(projection)
    await expect(expressions.get_by_role("textbox", name="行过滤", exact=True)).to_have_value(row_filter)
    await expressions.get_by_role("button", name="取消", exact=True).click()
    await rename_task(page, "Mapped orders renamed")
    async with page.expect_response(lambda response: response.request.method == "PUT"
                                    and response.url.endswith("/api/v1/task-definitions/new-task")):
        await page.get_by_role("button", name="保存", exact=True).click()
    last_write = [request[2] for request in state["requests"]
                  if request[0] == "PUT" and request[1] == "/api/v1/task-definitions/new-task"][-1]
    assert "parameters" not in last_write
    assert state["task"]["parameters"] == authored

    await pick_option(page, source_select, "app.orders_archive")
    confirm = page.get_by_role("dialog", name="更换来源表", exact=True)
    await expect(confirm).to_be_visible()
    await confirm.get_by_role("button", name="取消", exact=True).click()
    expressions = await open_field_expressions(page)
    await expect(expressions.get_by_role("textbox", name="投影表达式", exact=True)).to_have_value(projection)
    await expressions.get_by_role("button", name="取消", exact=True).click()
    await pick_option(page, source_select, "app.orders_archive")
    await confirm.get_by_role("button", name="确认", exact=True).click()
    expressions = await open_field_expressions(page)
    await expect(expressions.get_by_role("textbox", name="行过滤", exact=True)).to_have_value("")
    await expect(expressions.get_by_role("textbox", name="输出主键", exact=True)).to_have_value("")
    await expressions.get_by_role("button", name="取消", exact=True).click()
    assert state["task"]["parameters"] == authored, "Unsaved reset must not modify the saved definition"


async def verify_snapshot_replication(page):
    """Bounded replication stays in its product family and histories retain their version."""
    from datetime import datetime, timezone
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库实时")
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await set_read_strategy(page, "全量后结束")
    await pick_datasources(page)
    await select_source_tables(page, ["app.batch_a", "app.batch_b"])
    for table in ("batch_a", "batch_b"):
        await set_mapping_target(page, "app." + table, "ods.public." + table + "_copy")
    advanced = await open_advanced(page, "DDL 能力配置")
    await expect(advanced.get_by_role("switch", name="同步结构变更", exact=True)).to_have_count(0)
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#schedule")).to_be_visible()
    await expect(advanced.locator("#env").get_by_text("执行完成后结束", exact=True)).to_be_visible()
    await configure_cron(page, "0 0 8 * * ?")
    await apply_drawer(advanced)
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
    await expect_read_strategy(page, "全量后结束")
    properties = await open_properties(page)
    await expect(properties.get_by_text("整库实时 · 全量后结束", exact=True)).to_be_visible()
    await properties.get_by_role("button", name="取消", exact=True).click()
    advanced = await open_advanced(page, parameter_tab="运行时配置")
    await expect(advanced.locator("#schedule")).to_be_visible()
    await advanced.get_by_role("button", name="取消", exact=True).click()
    await page.get_by_role("button", name="发布", exact=True).click()
    await expect(page.get_by_text("本次发布 V1 已确认", exact=True)).to_be_visible()
    await page.get_by_role("button", name="任务列表", exact=True).click()
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

    # Publish a new current definition fixture; the selected instance is still version 1.
    current = deepcopy(saved)
    current["source"]["startup"] = {"mode": "INITIAL"}
    current["pipeline"]["execution.runtime-mode"] = "STREAMING"
    for table in current["sink"]["tables"]:
        table["write"] = {"mode": "UPSERT"}
    state["task"] = definition_response({
        **state["task"], "definitionVersion": 2, "parameters": current,
        "publishedVersion": 2, "revision": state["task"]["revision"] + 1,
    })
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
