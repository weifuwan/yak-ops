import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  batchDeleteDataSources,
  batchTestDataSourceConnections,
  deleteDataSource,
  getDataSource,
  type DataSourceRecord,
} from "@/service/datasource";
import { useIntl } from "../i18n";
import { useWorkspace } from "@/hooks/use-workspace";
import { DataSourceForm } from "../form/data-source-dialog";
import { DataSourceFilters } from "./data-source-filters";
import DataSourceTable from "./data-source-table";
import { dataSourceListKeys, useDataSources } from "./use-data-sources";

const DEFAULT_PAGE_SIZE = 10;
const MAX_BATCH_SELECTION = 100;

export function DataSourcePage() {
  const intl = useIntl();
  const { currentWorkspace } = useWorkspace();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const requestedReturn = searchParams.get("returnTo");
  const returnTo =
    requestedReturn && /^\/data-sync\/(?:new|[A-Za-z0-9_-]+\/edit)$/.test(requestedReturn)
      ? requestedReturn
      : undefined;
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [keyword, setKeywordState] = useState("");
  const [dbType, setDbTypeState] = useState<string>();
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<DataSourceRecord>();
  const [editingId, setEditingId] = useState("");
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [pendingDelete, setPendingDelete] = useState<DataSourceRecord>();
  const [deleting, setDeleting] = useState(false);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [batchDeleting, setBatchDeleting] = useState(false);
  const [batchTesting, setBatchTesting] = useState(false);

  const queryClient = useQueryClient();
  const pageQuery = useDataSources({ pageNo, pageSize, keyword, dbType });
  const records = pageQuery.data?.totalList ?? [];
  const total = pageQuery.data?.total ?? 0;
  const loading = pageQuery.isPending || pageQuery.isFetching;

  useEffect(() => {
    if (pageQuery.isSuccess && records.length === 0 && total > 0 && pageNo > 1) {
      setPageNo((current) => Math.max(1, current - 1));
    }
  }, [pageQuery.isSuccess, records.length, total, pageNo]);

  useEffect(() => {
    if (searchParams.get("create") !== "1") return;
    setEditingRecord(undefined);
    setFormOpen(true);
  }, [searchParams]);

  const hasActiveFilters = Boolean(keyword.trim() || dbType);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: dataSourceListKeys.all });

  const setKeyword = (value: string) => {
    setKeywordState(value);
    setSelectedRowKeys([]);
    setPageNo(1);
  };

  const setDbType = (value?: string) => {
    setDbTypeState(value);
    setSelectedRowKeys([]);
    setPageNo(1);
  };

  const handleSelectionChange = (keys: string[]) => {
    if (keys.length > MAX_BATCH_SELECTION) {
      toast.warning(
        intl.formatMessage(
          { id: "pages.datasource.batch.selectionLimit" },
          { count: MAX_BATCH_SELECTION },
        ),
      );
      return;
    }
    setSelectedRowKeys(keys);
  };

  const handleCreate = () => {
    setEditingRecord(undefined);
    setFormOpen(true);
  };

  const handleFormOpenChange = (open: boolean) => {
    setFormOpen(open);
    if (open || searchParams.get("create") !== "1") return;
    const nextSearchParams = new URLSearchParams(searchParams);
    nextSearchParams.delete("create");
    setSearchParams(nextSearchParams, { replace: true });
  };

  const handleEdit = async (record: DataSourceRecord) => {
    if (!currentWorkspace) return;
    if (!record.id || editingId) return;
    const id = String(record.id);
    setEditingId(id);
    try {
      const detail = await getDataSource(record.id);
      setEditingRecord(detail);
      setFormOpen(true);
    } finally {
      setEditingId("");
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await deleteDataSource(pendingDelete.id);
      toast.success(intl.formatMessage({ id: "pages.datasource.delete.success" }));
      setSelectedRowKeys((keys) => keys.filter((id) => id !== String(pendingDelete.id)));
      setPendingDelete(undefined);
      refresh();
    } finally {
      setDeleting(false);
    }
  };

  const confirmBatchDelete = async () => {
    if (selectedRowKeys.length === 0 || batchDeleting) return;
    setBatchDeleting(true);
    try {
      await batchDeleteDataSources(selectedRowKeys);
      toast.success(
        intl.formatMessage(
          { id: "pages.datasource.batch.deleteSuccess" },
          { count: selectedRowKeys.length },
        ),
      );
      setBatchDeleteOpen(false);
      setSelectedRowKeys([]);
      refresh();
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "批量删除部分失败，请刷新后重试");
    } finally {
      // A partially successful batch must still refresh the remaining records.
      refresh();
      setBatchDeleting(false);
    }
  };

  const handleBatchTestConnection = async () => {
    if (selectedRowKeys.length === 0 || batchTesting) return;
    setBatchTesting(true);
    try {
      const results = await batchTestDataSourceConnections(selectedRowKeys);
      const successCount = results.filter((result) => result.connected).length;
      const failedCount = results.length - successCount;
      const message = intl.formatMessage(
        { id: "pages.datasource.batch.testResult" },
        { success: successCount, failed: failedCount },
      );
      if (failedCount > 0) toast.warning(message);
      else toast.success(message);
      setSelectedRowKeys([]);
      refresh();
    } finally {
      setBatchTesting(false);
    }
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader
          title={intl.formatMessage({ id: "pages.datasource.page.title" })}
          extra={
            returnTo ? (
              <Button variant="secondary" size="sm" onClick={() => navigate(returnTo)}>
                返回同步任务
              </Button>
            ) : undefined
          }
          bordered
          className="bg-white px-6 max-md:px-4"
        />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <DataSourceFilters
              keyword={keyword}
              dbType={dbType}
              onCreate={handleCreate}
              onKeywordChange={setKeyword}
              onDbTypeChange={setDbType}
            />

            <section className="mt-4 min-h-0 flex-1">
              {pageQuery.isError ? (
                <div
                  role="alert"
                  className="mb-3 flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
                >
                  数据源加载失败
                  <Button variant="secondary" size="sm" onClick={() => void pageQuery.refetch()}>
                    重试
                  </Button>
                </div>
              ) : null}
              <DataSourceTable
                records={records}
                loading={loading}
                pageNo={pageNo}
                pageSize={pageSize}
                total={total}
                hasActiveFilters={hasActiveFilters}
                editingId={editingId}
                selectedRowKeys={selectedRowKeys}
                batchDeleting={batchDeleting}
                batchTesting={batchTesting}
                onPageChange={(nextPage, nextPageSize) => {
                  setPageNo(nextPage);
                  setPageSize(nextPageSize);
                }}
                onSelectionChange={handleSelectionChange}
                onEdit={(record) => void handleEdit(record)}
                onDelete={setPendingDelete}
                onBatchDelete={() => setBatchDeleteOpen(true)}
                onBatchTestConnection={() => void handleBatchTestConnection()}
              />
            </section>
          </div>
        </div>
      </div>

      <DataSourceForm
        open={formOpen}
        record={editingRecord}
        onOpenChange={handleFormOpenChange}
        onSaved={refresh}
      />

      <Dialog
        open={batchDeleteOpen}
        onOpenChange={(open) => {
          if (!batchDeleting) setBatchDeleteOpen(open);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">
            {intl.formatMessage({ id: "pages.datasource.batch.deleteConfirmTitle" })}
          </DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            {intl.formatMessage(
              { id: "pages.datasource.batch.deleteContent" },
              { count: selectedRowKeys.length },
            )}
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="secondary" isDisabled={batchDeleting} onClick={() => setBatchDeleteOpen(false)}>
              {intl.formatMessage({ id: "pages.datasource.delete.cancelText" })}
            </Button>
            <Button
              variant="danger"
              isPending={batchDeleting}
              onClick={() => void confirmBatchDelete()}
            >
              {intl.formatMessage({ id: "pages.datasource.batch.delete" })}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !deleting) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">
            {intl.formatMessage({ id: "pages.datasource.delete.confirmTitle" })}
          </DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            {intl.formatMessage(
              { id: "pages.datasource.delete.content" },
              { name: pendingDelete?.name || "-" },
            )}
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>
              {intl.formatMessage({ id: "pages.datasource.delete.cancelText" })}
            </Button>
            <Button variant="danger" isPending={deleting} onClick={() => void confirmDelete()}>
              {intl.formatMessage({ id: "pages.datasource.delete.okText" })}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
