"""UI-only schedule history, state, conflict and Workspace scenarios."""
import os
import re
from pathlib import Path
from playwright.async_api import expect
from mock_api import state
from data_sync import verify_revision_conflict, site

async def verify_schedule_history(page):
    await page.goto(site("/data-sync?productType=SINGLE_TABLE_BATCH"))
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
        path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "fixture-history-desktop.png"),
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
        path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "fixture-history-mobile.png"),
        full_page=True,
    )
    await page.keyboard.press("Escape")
    await expect(page.get_by_role("dialog")).to_have_count(0)
    await page.set_viewport_size({"width": 1440, "height": 1000})
    await verify_revision_conflict(page)

async def verify_schedule_errors(page):
    state["fail_batch"] = True
    await page.goto(site("/data-sync?productType=SINGLE_TABLE_BATCH"))
    await expect(page.get_by_role("button", name="调度加载失败", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="手动", exact=True)).to_have_count(0)
    state["fail_batch"] = False
    await page.get_by_role("button", name="Workspace A", exact=True).click()
    await page.get_by_role("menuitem", name="Workspace B", exact=True).click()
    await expect(page.get_by_role("button", name="手动", exact=True)).to_be_visible()
    assert [x for x in state["requests"] if x[1] == "/api/v1/schedules/page"][-1][3] == "ws2"
