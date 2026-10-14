"""Real browser creation, draft, duplicate-target guard, and table results for a batch task."""
import json
from pathlib import Path
import re
import uuid
from playwright.sync_api import sync_playwright, expect


def verify_multitable_browser(api, client, source, target):
    frontend = "http://127.0.0.1:5173"
    directory = Path("/tmp/yak-schedule-browser")
    directory.mkdir(parents=True, exist_ok=True)
    jar = next(handler.cookiejar for handler in client.opener.handlers if hasattr(handler, "cookiejar"))
    source_name = client.call("GET", f"/api/v1/data-source/{source}")["name"]
    target_name = client.call("GET", f"/api/v1/data-source/{target}")["name"]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        context.add_cookies([{"name": cookie.name, "value": cookie.value, "url": frontend, "httpOnly": True, "sameSite": "Lax"} for cookie in jar])
        context.add_init_script("localStorage.setItem('yak-ops.current-workspace-id'," + json.dumps(client.workspace) + ");")
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        try:
            page.goto(frontend + "/data-sync?create=1")
            dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
            def select_option(trigger, name):
                trigger.click()
                popup = page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]').last
                listbox = popup.get_by_role("listbox")
                list_id = listbox.get_attribute("id")
                assert list_id, "Select listbox is missing its identifier"
                listbox.get_by_role("option", name=name, exact=True).click()
                expect(page.locator(f'[id="{list_id}"]')).to_be_hidden()
                # Wait for the previous select portal before opening another dropdown.
                expect(page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]')).to_have_count(0)

            for index, name in enumerate(["MySQL", "PostgreSQL", "整库离线"]):
                select_option(dialog.get_by_role("combobox").nth(index), name)
            dialog.get_by_role("textbox", name="名称", exact=True).fill("browser-batch-" + uuid.uuid4().hex[:8])
            dialog.get_by_role("button", name="下一步", exact=True).click()
            for index, name in enumerate([source_name, target_name]):
                select_option(page.locator("#datasource").get_by_role("combobox").nth(index), name)
            for table in ("batch_a", "batch_b"):
                page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
            def choose(source_table, target_table):
                select_option(
                    page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + source_table + r"$")),
                    re.compile(r"(.*\.)?" + target_table + r"$"),
                )
            choose("batch_a", "batch_a_copy")
            choose("batch_b", "batch_a_copy")
            expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
            expect(page.get_by_text("去向表不能重复", exact=True).first).to_be_visible()
            choose("batch_b", "batch_b_copy")
            select_option(page.locator("#target").get_by_role("combobox").first, "按主键更新")
            page.reload()
            expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
            page.get_by_role("button", name="保存", exact=True).click()
            expect(page).to_have_url(re.compile(r"/data-sync/[A-Za-z0-9_-]+/edit$"))
            identity = page.url.split("/")[-2]
            parameters = json.loads(client.call("GET", f"/api/v1/task-definitions/{identity}")["parameters"])
            assert parameters["solutionType"] == "DATABASE_OFFLINE"
            assert len(parameters["tableMappings"]) == 2
            assert [row["target"]["table"] for row in parameters["tableMappings"]] == ["batch_a_copy", "batch_b_copy"]
            assert all(row["writeMode"] == "UPSERT" for row in parameters["tableMappings"])
            page.get_by_role("button", name="保存并上线", exact=True).click()
            expect(page).to_have_url(frontend + "/data-sync")
            submitted, _ = api["submit"](client, identity)
            api["wait_status"](client, submitted["instanceId"], {"FINISHED"})
            page.goto(f"{frontend}/data-sync/{identity}/detail?executionId={submitted['instanceId']}")
            for table in ("batch_a", "batch_b"):
                expect(page.get_by_role("progressbar", name=re.compile(r"(.*\.)?" + table + r" 全量进度$"))).to_have_attribute("aria-valuenow", "100")
            page.screenshot(path=str(directory / "multitable-results-desktop.png"), full_page=True)
            page.set_viewport_size({"width": 390, "height": 844})
            page.emulate_media(reduced_motion="reduce")
            page.screenshot(path=str(directory / "multitable-results-mobile.png"), full_page=True)
            assert not errors, errors
            print("MULTITABLE BROWSER PASS: real creation, explicit selection, duplicate guard, restored draft, save/publication and measured results", flush=True)
        except Exception:
            page.screenshot(path=str(directory / "multitable-failure.png"), full_page=True)
            raise
        finally:
            context.close()
            browser.close()
