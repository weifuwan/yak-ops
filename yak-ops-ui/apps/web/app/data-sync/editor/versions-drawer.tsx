import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Badge,
  Button,
  Table,
  Tabs,
  TabsList,
  TabsTab,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  getTaskParameterVersion,
  listTaskParameterVersions,
  listTaskPublications,
  type DataSyncTaskRecord,
  type TaskParameterVersion,
  type TaskParameterVersionSummary,
  type TaskPublicationRecord,
} from "@/service/data-sync";
import { compareParameterVersions, type ParameterChange } from "@/service/data-sync/versions";
import { parseSyncParameters } from "@/service/data-sync/parameters";
import { EditorDrawer } from "./editor-drawer";

const PAGE_SIZE = 20;
type VersionTask = Pick<DataSyncTaskRecord, "id" | "definitionVersion" | "publishedVersion">;
const positiveInteger = (value: string | null, fallback = 0) =>
  value && /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0
    ? Number(value)
    : fallback;

function VersionDetail({
  value,
  compare,
  current,
  onRestore,
  allowRestore,
}: {
  value: TaskParameterVersion;
  compare?: TaskParameterVersion;
  current: VersionTask;
  onRestore: (value: TaskParameterVersion) => void;
  allowRestore: boolean;
}) {
  let error = "";
  let changes: ParameterChange[] | undefined;
  try {
    parseSyncParameters(value.parameterSchemaVersion, value.parameters);
    if (compare) changes = compareParameterVersions(value, compare);
  } catch (failure) {
    error = failure instanceof Error ? failure.message : "版本参数无法解析";
  }
  return (
    <section className="mt-5 space-y-3 border-t border-[var(--yak-components-panel-border)] pt-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-sm font-semibold">
          {compare ? `V${value.version} → V${compare.version}` : `参数版本 V${value.version}`}
        </h3>
        <Button
          size="small"
          disabled={Boolean(error) || !allowRestore}
          onClick={() => onRestore(value)}
        >
          还原到当前编辑
        </Button>
      </div>
      {error ? <Alert>{error}，此版本仅支持查看。</Alert> : null}
      {!compare && value.version === current.publishedVersion ? (
        <Badge tone="success">当前发布版本</Badge>
      ) : null}
      {changes ? (
        <Table<ParameterChange>
          rowKey="field"
          size="small"
          columns={[
            { key: "field", title: "参数", dataIndex: "field", minWidth: 180 },
            {
              key: "before",
              title: `V${value.version}`,
              minWidth: 220,
              render: (_, row) => (
                <pre className="whitespace-pre-wrap break-all font-mono text-xs">{row.before}</pre>
              ),
            },
            {
              key: "after",
              title: `V${compare!.version}`,
              minWidth: 220,
              render: (_, row) => (
                <pre className="whitespace-pre-wrap break-all font-mono text-xs">{row.after}</pre>
              ),
            },
          ]}
          dataSource={changes}
          pagination={false}
          emptyText="两个版本的参数一致"
          scroll={{ x: 650 }}
        />
      ) : (
        <pre
          aria-label={`参数版本 V${value.version}`}
          className="max-h-[440px] overflow-auto rounded border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] p-3 text-xs leading-5"
        >
          {JSON.stringify(value.parameters, null, 2)}
        </pre>
      )}
    </section>
  );
}

export function VersionsDrawer({
  task,
  onClose,
  onRestore,
  allowRestore = true,
}: {
  task?: VersionTask;
  onClose: () => void;
  onRestore: (value: TaskParameterVersion) => void;
  allowRestore?: boolean;
}) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const [search, setSearch] = useSearchParams();
  const tab = search.get("versionsTab") === "publications" ? "publications" : "development";
  const page = positiveInteger(search.get("versionsPage"), 1);
  const version = positiveInteger(search.get("version"));
  const compareVersion = positiveInteger(search.get("compareVersion"));
  const scope = [currentUser?.id, currentWorkspace?.id, task?.id];
  const enabled = Boolean(task && currentUser && currentWorkspace);
  const updateSearch = (values: Record<string, string | undefined>) =>
    setSearch(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(values)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        return next;
      },
      { replace: true },
    );
  const versions = useQuery({
    queryKey: ["data-sync-versions", ...scope, "development", page],
    enabled: enabled && tab === "development",
    queryFn: ({ signal }) => listTaskParameterVersions(task!.id, page, PAGE_SIZE, signal),
  });
  const publications = useQuery({
    queryKey: ["data-sync-versions", ...scope, "publications", page],
    enabled: enabled && tab === "publications",
    queryFn: ({ signal }) => listTaskPublications(task!.id, page, PAGE_SIZE, signal),
  });
  const detail = useQuery({
    queryKey: ["data-sync-versions", ...scope, "detail", version],
    enabled: enabled && version > 0,
    queryFn: ({ signal }) => getTaskParameterVersion(task!.id, version, signal),
    staleTime: Infinity,
  });
  const comparison = useQuery({
    queryKey: ["data-sync-versions", ...scope, "detail", compareVersion],
    enabled: enabled && version > 0 && compareVersion > 0,
    queryFn: ({ signal }) => getTaskParameterVersion(task!.id, compareVersion, signal),
    staleTime: Infinity,
  });
  const query = tab === "development" ? versions : publications;
  const view = (number: number) =>
    updateSearch({ version: String(number), compareVersion: undefined });
  const compare = (number: number) =>
    updateSearch({ version: String(number), compareVersion: String(task!.definitionVersion) });
  const columns: TableColumns<TaskParameterVersionSummary> = [
    {
      key: "version",
      title: "参数版本",
      render: (_, row) => (
        <div className="flex flex-wrap items-center gap-2">
          <span>V{row.version}</span>
          {row.version === task?.publishedVersion ? <Badge tone="success">已发布</Badge> : null}
        </div>
      ),
    },
    { key: "operator", title: "操作人", dataIndex: "createBy", width: 150 },
    { key: "time", title: "时间", dataIndex: "createTime", width: 180 },
    {
      key: "actions",
      title: "操作",
      width: 170,
      render: (_, row) => (
        <div className="flex gap-1">
          <Button size="small" variant="ghost" onClick={() => view(row.version)}>
            查看
          </Button>
          <Button size="small" variant="ghost" onClick={() => compare(row.version)}>
            对比
          </Button>
        </div>
      ),
    },
  ];
  const publicationColumns: TableColumns<TaskPublicationRecord> = [
    { key: "version", title: "参数版本", render: (_, row) => `V${row.version}` },
    { key: "debug", title: "Debug", render: (_, row) => (row.debug ? "开启" : "关闭") },
    { key: "operator", title: "发布人", dataIndex: "createBy", width: 140 },
    { key: "time", title: "发布时间", dataIndex: "createTime", width: 180 },
    {
      key: "actions",
      title: "操作",
      width: 85,
      render: (_, row) => (
        <Button size="small" variant="ghost" onClick={() => view(row.version)}>
          查看
        </Button>
      ),
    },
  ];
  const pagination = {
    current: page,
    pageSize: PAGE_SIZE,
    total: query.data?.pagination.total || 0,
    onChange: (next: number) => updateSearch({ versionsPage: String(next) }),
  };
  return (
    <EditorDrawer title="版本" width={920} onClose={onClose}>
      {!task ? (
        <p className="text-sm text-[var(--yak-components-muted-text)]">
          保存任务后可查看参数版本。
        </p>
      ) : (
        <>
          <Tabs
            value={tab}
            onValueChange={(next) =>
              updateSearch({ versionsTab: String(next), versionsPage: undefined })
            }
          >
            <TabsList>
              <TabsTab value="development">开发记录</TabsTab>
              <TabsTab value="publications">发布记录</TabsTab>
            </TabsList>
          </Tabs>
          <div className="my-4 flex justify-end">
            <Button size="small" loading={query.isFetching} onClick={() => void query.refetch()}>
              刷新
            </Button>
          </div>
          {query.isError ? (
            <Alert>{query.error instanceof Error ? query.error.message : "版本记录加载失败"}</Alert>
          ) : tab === "development" ? (
            <Table<TaskParameterVersionSummary>
              rowKey="version"
              columns={columns}
              dataSource={versions.data?.bizData || []}
              loading={versions.isPending}
              size="small"
              pagination={pagination}
              rowSelection={{
                selectedRowKeys: [version, compareVersion].filter(Boolean),
                hideSelectAll: true,
                onChange: (keys) => {
                  const pair = keys.slice(-2);
                  updateSearch({
                    version: pair[0] ? String(pair[0]) : undefined,
                    compareVersion: pair[1] ? String(pair[1]) : undefined,
                  });
                },
              }}
              emptyText="暂无开发记录"
            />
          ) : (
            <Table<TaskPublicationRecord>
              rowKey="id"
              columns={publicationColumns}
              dataSource={publications.data?.bizData || []}
              loading={publications.isPending}
              size="small"
              pagination={pagination}
              emptyText="暂无发布记录"
            />
          )}
          {detail.isError || (compareVersion > 0 && comparison.isError) ? (
            <Alert className="mt-4">
              参数版本加载失败，请重试。
              <Button
                size="small"
                onClick={() => {
                  void detail.refetch();
                  if (compareVersion) void comparison.refetch();
                }}
              >
                重试
              </Button>
            </Alert>
          ) : version && (!detail.data || (compareVersion && !comparison.data)) ? (
            <p className="mt-5 text-sm">正在加载参数版本…</p>
          ) : detail.data ? (
            <VersionDetail
              value={detail.data}
              compare={compareVersion ? comparison.data : undefined}
              current={task}
              onRestore={onRestore}
              allowRestore={allowRestore}
            />
          ) : null}
        </>
      )}
    </EditorDrawer>
  );
}
