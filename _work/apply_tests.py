from apply_model import ROOT, P, C, J, edit, write

A='yak-ops-ui/apps/web/app/data-sync/'
# Complete the resource select contract, including accessible names on the actual trigger.
edit(A+'table-mapping-editor.tsx', 'value={row.writeMode} options=', 'placeholder="请选择写入方式" value={row.writeMode} options=')
edit(A+'table-mapping-editor.tsx', 'aria-label={`去向表 ${pathText(row.source)}`}', 'ariaLabel={`去向表 ${pathText(row.source)}`}')
edit(A+'searchable-select.tsx', '  placeholder: string;', '  placeholder: string;\n  ariaLabel?: string;')
edit(A+'searchable-select.tsx', '  placeholder,\n', '  placeholder,\n  ariaLabel,\n')
edit(A+'searchable-select.tsx', '<SelectTrigger variant="outlined">', '<SelectTrigger variant="outlined" aria-label={ariaLabel}>')

T='yak-ops-plugins/yak-ops-plugin-task/yak-ops-plugin-task-data-sync/src/test/java/io/yak/ops/plugin/task/datasync/'
path=T+'DataSyncProductParametersTest.java'
edit(path, 'List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.SINGLE_TABLE_REALTIME)', 'List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.SINGLE_TABLE_REALTIME, SolutionType.DATABASE_OFFLINE)')
edit(path, 'List.of(SolutionType.SINGLE_TABLE_OFFLINE), plugin.solutions', 'List.of(SolutionType.SINGLE_TABLE_OFFLINE, SolutionType.DATABASE_OFFLINE), plugin.solutions')
edit(path, 'assertEquals(1, plugin.solutions("ORACLE", "ORACLE").size());', 'assertEquals(2, plugin.solutions("ORACLE", "ORACLE").size());')
write(T+'MultiTableBatchContractTest.java', r'''
package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.*;
import io.yak.ops.configuration.ConfigParser;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.TableSchema;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceTable;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.datasync.config.JobConfig;
import io.yak.ops.plugin.task.datasync.execution.TableExecutionProgress;
import io.yak.ops.plugin.task.datasync.plan.JobPlan;
import io.yak.ops.plugin.task.datasync.plan.JobPlanner;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.common.util.JSONUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Product cardinality, resolved resources, and truthful bounded-table observations. */
class MultiTableBatchContractTest {
    private final DataSyncTaskPlugin plugin = DataSyncTaskPluginContractTest.plugin();

    @Test
    void acceptsOneOrManyExplicitRoutesWithoutChangingTheirSolutionIdentity() {
        for (int count : List.of(1, 3, 100)) {
            var parsed = plugin.parse(json(count));
            var job = JobConfig.from(parsed);
            assertEquals(SolutionType.DATABASE_OFFLINE, job.solutionType());
            assertEquals(count, job.tables().size());
            assertTrue(plugin.supportsScheduling(parsed));
            assertEquals(parsed, plugin.parse(3, ConfigParser.toJson(parsed)));
            plugin.validateReferences(parsed, new DataSyncTaskPluginContractTest.Metadata());
            plugin.validate(parsed, new DataSyncTaskPluginContractTest.Metadata());
        }
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(0)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(101)));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("DATABASE_OFFLINE", "SINGLE_TABLE_OFFLINE")));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("EXPLICIT", "RULES")));
    }

    @Test
    void rejectsDuplicatePhysicalAliasesAfterResolvingTheAuthorizedNamespace() {
        String duplicate = json(2).replace("orders1", "orders0");
        assertThrows(TaskPluginException.class, () -> plugin.parse(duplicate));
        String alias = json(2).replace("\"table\":\"orders1\"", "\"database\":\"app\",\"table\":\"orders0\"");
        var parsed = plugin.parse(alias);
        assertThrows(TaskPluginException.class, () -> plugin.validateReferences(parsed, new DataSyncTaskPluginContractTest.Metadata()));
        assertThrows(TaskPluginException.class, () -> plugin.parse(json(2).replace("copy1", "copy0")));
    }

    @Test
    void pinsEveryTableAndClaimBeforeCreatingAnyPhysicalConnector() {
        var job = plan(3);
        assertEquals(3, job.tables().size());
        assertEquals(3, job.admissionPlan().exclusiveResources().size());
        assertTrue(job.admissionPlan().exclusiveResources().stream().allMatch(value -> value.type().equals("DATA_SYNC_TARGET")));
        assertThrows(IllegalStateException.class, job::source);
        assertEquals(plan(3).admissionPlan(), job.admissionPlan());
        assertNotEquals(plan(2).admissionPlan().resourceRevision(), job.admissionPlan().resourceRevision());
    }

    @Test
    void sourceCompletionDoesNotPretendThatDestinationCommitHasCompleted() {
        JobPlan plan = plan(1);
        TableId table = plan.tables().getFirst().source().table();
        TableMetrics metrics = new TableMetrics();
        metrics.register(table);
        TableExecutionProgress progress = new TableExecutionProgress(plan.tables(), metrics);
        assertTrue(progress.percent().isEmpty());
        metrics.planned(table, 8);
        metrics.read(table);
        for (int index = 0; index < 3; index++) metrics.splitFinished(table);
        assertEquals(37, progress.percent().orElseThrow());
        for (int index = 3; index < 8; index++) metrics.splitFinished(table);
        var row = JSONUtils.readTree(progress.report(JobStatus.RUNNING)).path("tables").get(0);
        assertEquals("RUNNING", row.path("status").asText());
        assertEquals(99, row.path("progressPercent").asInt());
        assertEquals(0, row.path("committedRows").asInt());
        metrics.committed(table, 1);
        row = JSONUtils.readTree(progress.report(JobStatus.RUNNING)).path("tables").get(0);
        assertEquals("FINISHED", row.path("status").asText());
        assertEquals(100, row.path("progressPercent").asInt());
    }

    @Test
    void totalDenominatorWaitsForEveryTableAndFailureRetainsCommittedWork() {
        JobPlan plan = plan(2);
        TableId first = plan.tables().get(0).source().table();
        TableId second = plan.tables().get(1).source().table();
        TableMetrics metrics = new TableMetrics();
        metrics.register(first);
        metrics.register(second);
        TableExecutionProgress progress = new TableExecutionProgress(plan.tables(), metrics);
        metrics.planned(first, 1);
        metrics.read(first);
        metrics.splitFinished(first);
        assertTrue(progress.percent().isEmpty());
        metrics.committed(first, 1);
        metrics.planned(second, 7);
        assertEquals(12, progress.percent().orElseThrow());
        metrics.writeFailed(second);
        var rows = JSONUtils.readTree(progress.report(JobStatus.FAILED)).path("tables");
        assertEquals("FINISHED", rows.get(0).path("status").asText());
        assertEquals(1, rows.get(0).path("committedRows").asInt());
        assertEquals("FAILED", rows.get(1).path("status").asText());
        assertEquals("WRITE_FAILED", rows.get(1).path("failureCode").asText());
        assertEquals("SPLITS", rows.get(1).path("progressUnit").asText());
        assertFalse(rows.toString().contains("password"));
    }

    @Test
    void anEmptyTableHasARealCompletionAndUnstartedTablesCancelWithoutPercent() {
        JobPlan plan = plan(2);
        TableMetrics metrics = new TableMetrics();
        metrics.planned(plan.tables().get(0).source().table(), 0);
        var rows = JSONUtils.readTree(new TableExecutionProgress(plan.tables(), metrics).report(JobStatus.CANCELED)).path("tables");
        assertEquals("FINISHED", rows.get(0).path("status").asText());
        assertEquals(100, rows.get(0).path("progressPercent").asInt());
        assertEquals("CANCELED", rows.get(1).path("status").asText());
        assertTrue(rows.get(1).path("progressPercent").isNull());
    }

    private JobPlan plan(int count) {
        var metadata = new DataSyncTaskPluginContractTest.Metadata();
        var lookup = new DataSourceLookupService() {
            @Override
            public DataSourceTable table(String workspace, String id, String database, String schema, String name) {
                assertEquals("ws", workspace);
                DataSourceInfo info = metadata.dataSource(id);
                TableId table = info.resolveTable(database, schema, name);
                return new DataSourceTable(info.type(), table, metadata.tableSchema(id, table), 1);
            }
            @Override
            public ReadonlyConfig connectionOptions(String workspace, String id) { throw new AssertionError("Planning cannot read secrets"); }
            @Override
            public ReadonlyConfig connectionOptions(String workspace, String id, long revision) { throw new AssertionError("Planning cannot read secrets"); }
            @Override
            public DataSourceInfo dataSource(String workspace, String id) { return metadata.dataSource(id); }
        };
        return new JobPlanner(lookup, new FactoryUtil()).plan(plugin.parse(json(count)), new TaskExecutionIdentity("ws", "task", 1, "instance"));
    }

    private static String json(int count) {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (int index = 0; index < count; index++) tables.add(Map.of(
                "source", Map.of("table", "orders" + index), "target", Map.of("table", "copy" + index), "writeMode", "UPSERT"));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("solutionType", "DATABASE_OFFLINE");
        values.put("steps", List.of("FULL"));
        values.put("source", Map.of("datasourceId", "source"));
        values.put("sink", Map.of("datasourceId", "target"));
        values.put("tableSelection", Map.of("mode", "EXPLICIT"));
        values.put("tableMappings", tables);
        return JSONUtils.toJson(values);
    }
}
''')
# Align complete product expectations with the newly implemented choice (do not remove assertions).
path='scripts/acceptance/task-execution-e2e.py'
edit(path, '== ["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME"]', '== ["SINGLE_TABLE_OFFLINE", "SINGLE_TABLE_REALTIME", "DATABASE_OFFLINE"]')
edit(path, 'solutions?sourceType=POSTGRE_SQL&targetType=MYSQL")) == 1', 'solutions?sourceType=POSTGRE_SQL&targetType=MYSQL")) == 2')
edit(path, '        verify_parameter_versions(client, source, target, jdbc)', '        from multitable_batch import verify_multitable_batch, verify_multitable_conflict\n        batch_evidence = verify_multitable_batch(globals(), client, source, target)\n        verify_parameter_versions(client, source, target, jdbc)')
edit(path, '        verify_busy_schedule(client, source, target)', '        verify_multitable_conflict(globals(), client, source, target)\n        verify_busy_schedule(client, source, target)')
edit(path, '        print(\n            "PRODUCT E2E PASS:', '        assert client.call("GET", f"/api/v1/task-instances/{batch_evidence[0]}")["executionReport"] == batch_evidence[1]\n        print(\n            "PRODUCT E2E PASS:')
write('scripts/acceptance/multitable_batch.py', r'''
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
''')
write('scripts/acceptance/multitable_browser.py', r'''
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
            for index, name in enumerate(["MySQL", "PostgreSQL", "整库离线"]):
                dialog.get_by_role("combobox").nth(index).click()
                page.get_by_role("option", name=name, exact=True).click()
            dialog.get_by_role("textbox", name="名称", exact=True).fill("browser-batch-" + uuid.uuid4().hex[:8])
            dialog.get_by_role("button", name="下一步", exact=True).click()
            for index, name in enumerate([source_name, target_name]):
                page.locator("#datasource").get_by_role("combobox").nth(index).click()
                page.get_by_role("option", name=name, exact=True).click()
            for table in ("batch_a", "batch_b"):
                page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
            def choose(source_table, target_table):
                page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + source_table + r"$")).click()
                page.get_by_role("option", name=re.compile(r"(.*\.)?" + target_table + r"$")).click()
            choose("batch_a", "batch_a_copy")
            choose("batch_b", "batch_a_copy")
            expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
            expect(page.get_by_text("去向表不能重复", exact=True).first).to_be_visible()
            choose("batch_b", "batch_b_copy")
            page.locator("#target").get_by_role("combobox").first.click()
            page.get_by_role("option", name="按主键更新", exact=True).click()
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
''')
# Add executable parser/projection coverage to the existing frontend contract gate.
path='yak-ops-ui/scripts/verify-data-sync-definition.mjs'
p=ROOT/path;p.write_text(p.read_text()+r'''
const batch = { ...offline, solutionType: "DATABASE_OFFLINE", tableMappings: [
  { source: { table: "orders" }, target: { schema: "public", table: "orders_copy" }, writeMode: "APPEND" },
  { source: { table: "users" }, target: { schema: "public", table: "users_copy" }, writeMode: "UPSERT" },
] };
const batchJson = createDefinitionRequest(batch).parameters;
assert.equal(readSyncParameters(3, batchJson).tableMappings.length, 2);
assert.deepEqual(toSyncTaskRecord({ ...definition, parameters: batchJson }).tableMappings.map((row) => row.target.table), ["orders_copy", "users_copy"]);
assert.throws(() => buildSyncParameters({ ...batch, tableMappings: [] }));
assert.throws(() => readSyncParameters(3, batchJson.replace("users_copy", "orders_copy")));
assert.throws(() => readSyncParameters(3, batchJson.replace("DATABASE_OFFLINE", "SINGLE_TABLE_OFFLINE")));
console.log("Explicit multi-table parameter and complete projection contracts passed");
''')
# Record the already merged delivery without changing acceptance or claiming the whole YOIP complete.
edit('docs/yoips/YOIP-5-data-integration-sync-product-model.md', 'Implementation: Pending', 'Implementation: [Unified product entry and versioned solutions — PR #1637](https://github.com/weifuwan/yak-ops/pull/1637); explicit multi-table batch implementation in progress')
print('Multi-table contract and real-database/browser acceptance cases added')
