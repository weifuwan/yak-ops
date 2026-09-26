import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  batchDeleteDataSources,
  batchTestDataSourceConnections,
  deleteDataSource,
  getDataSource,
  listDataSources,
} from "@/service/datasource";
import { COMMON_DB_OPTIONS } from "./constants";
import DataSourceForm from "./form";
import { useIntl } from "./i18n";
import DataSourceTable from "./table";
import type { DataSourceRecord } from "./types";

const DEFAULT_PAGE_SIZE = 10;
const MAX_BATCH_SELECTION = 100;

const DataSourcePage = () => {
  const intl = useIntl();
  const requestSequenceRef = useRef(0);
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<DataSourceRecord[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [keyword, setKeywordState] = useState("");
  const [dbType, setDbTypeState] = useState<string>();
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<DataSourceRecord>();
  const [editingId, setEditingId] = useState("");
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  const [pendingDelete, setPendingDelete] = useState<DataSourceRecord>();
  const [deleting, setDeleting] = useState(false);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [batchDeleting, setBatchDeleting] = useState(false);
  const [batchTesting, setBatchTesting] = useState(false);

  const hasActiveFilters = Boolean(keyword.trim() || dbType);

  const refresh = useCallback(() => {
    setRefreshVersion((value) => value + 1);
  }, []);

  const loadPage = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const result = await listDataSources({
        pageNo,
        pageSize,
        keyword: keyword.trim() || undefined,
        dbType,
      });
      if (requestSequence !== requestSequenceRef.current) return;

      const nextRecords = result?.bizData || [];
      const nextPagination = result?.pagination;
      if (nextRecords.length === 0 && (nextPagination?.total || 0) > 0 && pageNo > 1) {
        setPageNo((value) => Math.max(1, value - 1));
        return;
      }

      setRecords(nextRecords);
      setTotal(nextPagination?.total || 0);
    } finally {
      if (requestSequence === requestSequenceRef.current) setLoading(false);
    }
  }, [dbType, keyword, pageNo, pageSize, refreshVersion]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadPage(), keyword.trim() ? 300 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadPage]);

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

  const handleEdit = async (record: DataSourceRecord) => {
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
    } finally {
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
      <div className="flex min-h-full flex-col bg-[#F6F6F6] text-[#242731]">
        <PageHeader
          title={intl.formatMessage({ id: "pages.datasource.page.title" })}
          bordered
          className="bg-white px-6 max-md:px-4"
        />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 flex-wrap items-center gap-2">
              <Button size="small" variant="primary" onClick={handleCreate}>
                <Plus size={14} />
                {intl.formatMessage({ id: "pages.datasource.page.create" })}
              </Button>

              <div className="w-[290px]">
                <Select
                  size="small"
                  value={dbType || "ALL"}
                  onValueChange={(value) => setDbType(value && value !== "ALL" ? value : undefined)}
                >
                  <SelectTrigger variant="outlined">
                    <span className="mr-2 text-[#4f5561]">
                      {intl.formatMessage({ id: "pages.datasource.toolbar.typeLabel" })}
                    </span>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">
                      <SelectItemText>
                        {intl.formatMessage({ id: "pages.datasource.toolbar.allTypes" })}
                      </SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    {COMMON_DB_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        <SelectItemText>{option.label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="relative w-[290px]">
                <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-font-size-control-small)] text-[#4f5561]">
                  {intl.formatMessage({ id: "pages.datasource.toolbar.nameLabel" })}
                </span>
                <Input
                  size="small"
                  variant="outlined"
                  value={keyword}
                  className="pl-[92px]"
                  placeholder={intl.formatMessage({
                    id: "pages.datasource.toolbar.namePlaceholder",
                  })}
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </div>
            </section>

            <section className="mt-4 min-h-0 flex-1">
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
        onOpenChange={setFormOpen}
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
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            {intl.formatMessage(
              { id: "pages.datasource.batch.deleteContent" },
              { count: selectedRowKeys.length },
            )}
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button disabled={batchDeleting} onClick={() => setBatchDeleteOpen(false)}>
              {intl.formatMessage({ id: "pages.datasource.delete.cancelText" })}
            </Button>
            <Button
              variant="danger"
              loading={batchDeleting}
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
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            {intl.formatMessage(
              { id: "pages.datasource.delete.content" },
              { name: pendingDelete?.name || "-" },
            )}
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button disabled={deleting} onClick={() => setPendingDelete(undefined)}>
              {intl.formatMessage({ id: "pages.datasource.delete.cancelText" })}
            </Button>
            <Button variant="danger" loading={deleting} onClick={() => void confirmDelete()}>
              {intl.formatMessage({ id: "pages.datasource.delete.okText" })}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
};

export { DataSourcePage };
export default DataSourcePage;
