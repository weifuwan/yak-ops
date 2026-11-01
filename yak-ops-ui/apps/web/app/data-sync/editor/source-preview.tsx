import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Modal, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { previewDataSourceTable, type DataSourcePreviewQuery } from "@/service/datasource";

export function SourcePreview({
  dataSourceId,
  query,
  supported,
}: {
  dataSourceId: string;
  query: DataSourcePreviewQuery;
  supported: boolean;
}) {
  const [request, setRequest] = useState<DataSourcePreviewQuery & { dataSourceId: string }>();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const result = useQuery({
    queryKey: ["data-sync-source-preview", currentUser?.id, currentWorkspace?.id, request],
    queryFn: ({ signal }) => {
      const { dataSourceId: id, ...payload } = request!;
      return previewDataSourceTable(id, payload, signal);
    },
    enabled: Boolean(request && currentUser?.id && currentWorkspace?.id),
    retry: false,
    gcTime: 0,
  });
  const rows = (result.data?.rows ?? []).map((cells, index) => ({ index, cells }));
  const columns: TableColumns<(typeof rows)[number]> = (result.data?.columns ?? []).map(
    (column, index) => ({
      key: index,
      title: (
        <span>
          {column.name}
          <span className="ml-2 font-normal text-[var(--yak-components-muted-text)]">
            {column.typeName}
          </span>
        </span>
      ),
      minWidth: 140,
      render: (_, row) =>
        row.cells[index] === null ? (
          <span className="text-[var(--yak-components-muted-text)]">NULL</span>
        ) : (
          String(row.cells[index])
        ),
    }),
  );
  return (
    <>
      <Button
        size="small"
        className="w-full"
        disabled={!supported || !dataSourceId || !query.table}
        title={!supported ? "当前来源不支持数据预览" : undefined}
        onClick={() => setRequest({ ...query, dataSourceId, limit: 20 })}
      >
        数据预览
      </Button>
      <Modal
        open={Boolean(request)}
        onClose={() => setRequest(undefined)}
        title="数据预览"
        width={1000}
        footer={
          <Button size="small" onClick={() => setRequest(undefined)}>
            关闭
          </Button>
        }
      >
        {result.isError ? (
          <Alert>
            数据预览失败
            <Button size="small" onClick={() => void result.refetch()}>
              重试
            </Button>
          </Alert>
        ) : (
          <Table
            columns={columns}
            dataSource={rows}
            rowKey="index"
            size="small"
            loading={result.isPending}
            pagination={false}
            scroll={{ x: true, y: 440 }}
            emptyText="没有符合条件的数据"
          />
        )}
        {result.data ? (
          <div className="mt-3 text-xs text-[var(--yak-components-muted-text)]">
            {result.data.truncated ? `结果已截断，显示 ${rows.length} 条` : `共 ${rows.length} 条`}
          </div>
        ) : null}
      </Modal>
    </>
  );
}
