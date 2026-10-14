"""Real browser creation, stage observations, cancellation, and isolated draft checks."""
import json
from pathlib import Path
import re
import uuid
from playwright.sync_api import sync_playwright, expect


def verify_database_realtime_browser(api, client, source, target, routes):
    frontend = "http://127.0.0.1:5173"
    directory = Path("/tmp/yak-schedule-browser")
    directory.mkdir(parents=True, exist_ok=True)
    jar = next(handler.cookiejar for handler in client.opener.handlers if hasattr(handler, "cookiejar"))
    names = [client.call("GET", f"/api/v1/data-source/{identity}")["name"] for identity in (source, target)]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        context.add_cookies([{"name": cookie.name, "value": cookie.value, "url": frontend, "httpOnly": True, "sameSite": "Lax"} for cookie in jar])
        context.add_init_script("localStorage.setItem('yak-ops.current-workspace-id'," + json.dumps(client.workspace) + ");")
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        identity = None
        try:
            def choose(trigger, name):
                trigger.click()
                popup = page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]').last
                listbox = popup.get_by_role("listbox")
                list_id = listbox.get_attribute("id")
                assert list_id
                listbox.get_by_role("option", name=name, exact=True).click()
                expect(page.locator(f'[id="{list_id}"]')).to_be_hidden()
                expect(page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]')).to_have_count(0)
            page.goto(frontend + "/data-sync?create=1")
            dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
            for index, name in enumerate(["MySQL", "PostgreSQL", "整库实时"]):
                choose(dialog.get_by_role("combobox").nth(index), name)
            for step in ("全量同步", "增量同步"):
                expect(dialog.get_by_role("checkbox", name=step, exact=True)).to_be_checked()
                expect(dialog.get_by_role("checkbox", name=step, exact=True)).to_be_disabled()
            dialog.get_by_role("textbox", name="名称", exact=True).fill("browser-realtime-" + uuid.uuid4().hex[:8])
            dialog.get_by_role("button", name="下一步", exact=True).click()
            for index, name in enumerate(names):
                choose(page.locator("#datasource").get_by_role("combobox").nth(index), name)
            for mapping in routes[:2]:
                table = mapping["source"]["table"]
                page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
                choose(page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + table + r"$")), re.compile(r"(.*\.)?" + mapping["target"]["table"] + r"$"))
            expect(page.locator("#target").get_by_role("combobox").first).to_be_disabled()
            expect(page.locator("#schedule")).to_have_count(0)
            page.reload()
            expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
            page.get_by_role("button", name="保存", exact=True).click()
            expect(page).to_have_url(re.compile(r"/data-sync/[A-Za-z0-9_-]+/edit$"))
            task = page.url.split("/")[-2]
            params = json.loads(client.call("GET", f"/api/v1/task-definitions/{task}")["parameters"])
            assert params["solutionType"] == "DATABASE_REALTIME" and params["steps"] == ["FULL", "INCREMENTAL"]
            assert len(params["tableMappings"]) == 2 and all(row["writeMode"] == "UPSERT" for row in params["tableMappings"])
            page.get_by_role("button", name="保存并上线", exact=True).click()
            expect(page).to_have_url(frontend + "/data-sync")
            submitted, _ = api["submit"](client, task)
            identity = submitted["instanceId"]
            page.goto(f"{frontend}/data-sync/{task}/detail?executionId={identity}")
            full = page.get_by_role("progressbar", name="全量同步", exact=True)
            incremental = page.get_by_role("progressbar", name="增量同步", exact=True)
            expect(full).to_be_visible()
            expect(incremental).to_be_visible()
            expect(full).to_have_attribute("aria-valuenow", "100", timeout=150000)
            expect(incremental).to_have_attribute("data-mode", "running", timeout=15000)
            assert incremental.get_attribute("aria-valuenow") is None
            for mapping in routes[:2]:
                table = mapping["source"]["table"]
                expect(page.get_by_role("progressbar", name=re.compile(r"(.*\.)?" + table + r" 全量进度$"))).to_have_attribute("aria-valuenow", "100")
                expect(page.get_by_role("progressbar", name=re.compile(r"(.*\.)?" + table + r" 增量状态$"))).to_have_attribute("data-mode", "running")
            page.screenshot(path=str(directory / "database-realtime-desktop.png"), full_page=True)
            # Lost polling cannot keep a forever-healthy streaming animation on screen.
            page.route("**/api/v1/task-instances/**", lambda route: route.abort())
            expect(incremental).to_have_attribute("aria-valuetext", "观测已过期", timeout=20000)
            page.unroute("**/api/v1/task-instances/**")
            expect(incremental).to_have_attribute("data-mode", "running", timeout=15000)
            client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
            api["wait_status"](client, identity, {"CANCELED"}, timeout=45)
            expect(incremental).not_to_have_attribute("data-mode", "running", timeout=15000)
            expect(full).to_have_attribute("aria-valuenow", "100")
            page.set_viewport_size({"width": 390, "height": 844})
            page.emulate_media(reduced_motion="reduce")
            page.screenshot(path=str(directory / "database-realtime-mobile.png"), full_page=True)
            assert not errors, errors
            print("DATABASE REALTIME BROWSER PASS: real creation, required steps, UPSERT, drafts, full-to-streaming stages, stale observations, cancel and narrow viewport", flush=True)
        except Exception:
            page.screenshot(path=str(directory / "database-realtime-failure.png"), full_page=True)
            raise
        finally:
            if identity:
                instance = client.call("GET", f"/api/v1/task-instances/{identity}")
                if instance["status"] in ("SUBMITTED", "LAUNCHING", "RUNNING"):
                    client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
                    api["wait_status"](client, identity, {"CANCELED", "FAILED"}, timeout=45)
            context.close()
            browser.close()
