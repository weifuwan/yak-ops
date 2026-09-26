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
import { Plus, Search } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import {
  deleteDataSource,
  getDataSource,
  listDataSources,
  testDataSourceConnection,
} from "@/service/datasource";
import { COMMON_DB_OPTIONS, CONNECTION_STATUS_OPTIONS } from "./constants";
import DataSourceForm from "./form";
import { useIntl } from "./i18n";
import DataSourceTable from "./table";
import type { DataSourceRecord } from "./types";

const DEFAULT_PAGE_SIZE = 10;

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
  const [connStatus, setConnStatusState] = useState<string>();
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<DataSourceRecord>();
  const [editingId, setEditingId] = useState("");
  const [testingId, setTestingId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<DataSourceRecord>();
  const [deleting, setDeleting] = useState(false);

  const hasActiveFilters = Boolean(keyword.trim() || dbType || connStatus);

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
        connStatus,
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
  }, [connStatus, dbType, keyword, pageNo, pageSize, refreshVersion]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadPage(), keyword.trim() ? 300 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadPage]);

  const setKeyword = (value: string) => {
    setKeywordState(value);
    setPageNo(1);
  };

  const setDbType = (value?: string) => {
    setDbTypeState(value);
    setPageNo(1);
  };

  const setConnStatus = (value?: string) => {
    setConnStatusState(value);
    setPageNo(1);
  };

  const resetFilters = () => {
    setKeywordState("");
    setDbTypeState(undefined);
    setConnStatusState(undefined);
    setPageNo(1);
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

  const handleTestConnection = async (record: DataSourceRecord) => {
    if (!record.id || testingId) return;
    const id = String(record.id);
    setTestingId(id);
    try {
      await testDataSourceConnection(record.id);
      toast.success(intl.formatMessage({ id: "pages.datasource.test.success" }));
      refresh();
    } finally {
      setTestingId("");
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await deleteDataSource(pendingDelete.id);
      toast.success(intl.formatMessage({ id: "pages.datasource.delete.success" }));
      setPendingDelete(undefined);
      refresh();
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="min-h-full bg-white text-[#242731]">
        <PageHeader
          title={intl.formatMessage({ id: "pages.datasource.page.title" })}
          extra={
            <Button variant="primary" onClick={handleCreate}>
              <Plus size={16} />
              {intl.formatMessage({ id: "pages.datasource.page.create" })}
            </Button>
          }
          bordered
          className="px-6 max-md:px-4"
        />

        <div className="px-6 pb-4 pt-5 max-md:px-4">
          <section className="flex flex-wrap items-center gap-2">
            <div className="relative w-[300px] max-md:w-full">
              <Search
                size={15}
                className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-[#98a2b3]"
              />
              <Input
                value={keyword}
                className="pl-9"
                placeholder={intl.formatMessage({
                  id: "pages.datasource.toolbar.searchPlaceholder",
                })}
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>

            <div className="w-[170px]">
              <Select
                value={dbType || "ALL"}
                onValueChange={(value) => setDbType(value && value !== "ALL" ? value : undefined)}
              >
                <SelectTrigger>
                  <SelectValue
                    placeholder={intl.formatMessage({
                      id: "pages.datasource.toolbar.typePlaceholder",
                    })}
                  />
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

            <div className="w-[150px]">
              <Select
                value={connStatus || "ALL"}
                onValueChange={(value) =>
                  setConnStatus(value && value !== "ALL" ? value : undefined)
                }
              >
                <SelectTrigger>
                  <SelectValue
                    placeholder={intl.formatMessage({
                      id: "pages.datasource.toolbar.statusPlaceholder",
                    })}
                  />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">
                    <SelectItemText>
                      {intl.formatMessage({ id: "pages.datasource.toolbar.allStatuses" })}
                    </SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  {CONNECTION_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <SelectItemText>
                        {intl.formatMessage({ id: option.messageId })}
                      </SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {hasActiveFilters ? (
              <Button variant="ghost" size="small" onClick={resetFilters}>
                {intl.formatMessage({ id: "pages.datasource.toolbar.reset" })}
              </Button>
            ) : null}
          </section>

          <section className="mt-4">
            <DataSourceTable
              records={records}
              loading={loading}
              pageNo={pageNo}
              pageSize={pageSize}
              total={total}
              hasActiveFilters={hasActiveFilters}
              editingId={editingId}
              testingId={testingId}
              onPageChange={(nextPage, nextPageSize) => {
                setPageNo(nextPage);
                setPageSize(nextPageSize);
              }}
              onEdit={(record) => void handleEdit(record)}
              onDelete={setPendingDelete}
              onTestConnection={(record) => void handleTestConnection(record)}
            />
          </section>
        </div>
      </div>

      <DataSourceForm
        open={formOpen}
        record={editingRecord}
        onOpenChange={setFormOpen}
        onSaved={refresh}
      />

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
