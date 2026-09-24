import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Pagination,
  Spinner,
  toast,
} from "@yak-ops/yak-ui";
import { motion } from "framer-motion";
import { useRef, useState } from "react";

import DataSourceEditor from "../editor/DataSourceEditor";
import type { DataSourceModalRef } from "../editor/types";
import { DataSourceOperateType } from "../editor/types";
import { useIntl } from "../i18n";
import {
  DATA_SOURCE_PAGE_SIZE_OPTIONS,
  PAGE_ANIMATION,
} from "../model/constants";
import { dataSourceRecordKey } from "../model/presentation";
import type { DataSourceRecord } from "../model/types";
import DataSourceCard from "./DataSourceCard";
import DataSourceEmptyState from "./DataSourceEmptyState";
import DataSourcePageHeader from "./DataSourcePageHeader";
import DataSourceSummaryCards from "./DataSourceSummaryCards";
import DataSourceToolbar from "./DataSourceToolbar";
import { useDataSourceManagement } from "./useDataSourceManagement";

const DataSourceManagement = () => {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);
  const [pendingDelete, setPendingDelete] = useState<DataSourceRecord>();
  const [deleting, setDeleting] = useState(false);

  const {
    loading,
    records,
    summary,
    pagination,
    keyword,
    dbType,
    environment,
    viewMode,
    hasActiveFilters,
    permissions,
    testingId,
    editingId,
    setKeyword,
    setDbType,
    setEnvironment,
    setViewMode,
    resetFilters,
    changePage,
    refresh,
    loadRecordForEdit,
    removeRecord,
    testRecord,
  } = useDataSourceManagement();

  const handleCreate = () => {
    if (!permissions.canCreate) return;
    modalRef.current?.open({
      operateType: DataSourceOperateType.Create,
      onSuccess: refresh,
    });
  };

  const handleEdit = async (record: DataSourceRecord) => {
    try {
      const detail = await loadRecordForEdit(record);
      if (!detail) return;

      modalRef.current?.open({
        operateType: DataSourceOperateType.Edit,
        currentRecord: detail,
        onSuccess: refresh,
      });
    } catch {
      // Shared request handling owns request failure feedback.
    }
  };

  const handleDelete = (record: DataSourceRecord) => {
    if (permissions.canDelete) setPendingDelete(record);
  };

  const confirmDelete = async () => {
    const record = pendingDelete;
    if (!record || deleting) return;

    if (record.id === undefined || record.id === null) {
      toast.error(
        intl.formatMessage({ id: "pages.datasource.delete.idMissing" }),
      );
      setPendingDelete(undefined);
      return;
    }

    try {
      setDeleting(true);
      const deleted = await removeRecord(record.id);
      if (deleted) {
        toast.success(
          intl.formatMessage({ id: "pages.datasource.delete.success" }),
        );
        setPendingDelete(undefined);
      }
    } catch {
      // Shared request handling owns request failure feedback.
    } finally {
      setDeleting(false);
    }
  };

  const handleTestConnection = async (record: DataSourceRecord) => {
    try {
      const connected = await testRecord(record);
      if (connected) {
        toast.success(
          intl.formatMessage({ id: "pages.datasource.test.success" }),
        );
      }
    } catch {
      // Shared request handling owns request failure feedback.
    }
  };

  return (
    <>
      <div className="min-h-[calc(100dvh-64px)] bg-[#f7f8fa] text-[#242731]">
        <motion.main
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
        >
          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 shadow-[0_2px_10px_rgba(31,35,41,0.025)] max-md:px-4"
            style={{ borderTopLeftRadius: 8, borderTopRightRadius: 8 }}
          >
            <div className="space-y-5">
              <DataSourcePageHeader
                canCreate={permissions.canCreate}
                onCreate={handleCreate}
              />
              <DataSourceSummaryCards summary={summary} />
              <DataSourceToolbar
                environment={environment}
                dbType={dbType}
                keyword={keyword}
                viewMode={viewMode}
                hasActiveFilters={hasActiveFilters}
                onEnvironmentChange={setEnvironment}
                onDbTypeChange={setDbType}
                onKeywordChange={setKeyword}
                onViewModeChange={setViewMode}
                onReset={resetFilters}
              />
            </div>

            <div className="mt-5 flex flex-1 flex-col">
              <div className="relative min-h-28">
                {loading ? (
                  <div className="absolute inset-0 z-20 flex items-center justify-center rounded-xl bg-white/75 backdrop-blur-[1px]">
                    <Spinner size="large" label="Loading datasources" />
                  </div>
                ) : null}

                <motion.section
                  variants={PAGE_ANIMATION.cardStagger}
                  initial="hidden"
                  animate="visible"
                  className={
                    viewMode === "list"
                      ? "grid grid-cols-1 gap-[14px]"
                      : "grid grid-cols-1 gap-[14px] md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4"
                  }
                >
                  {records.map((record, index) => (
                    <DataSourceCard
                      key={
                        dataSourceRecordKey(record.id) ||
                        `${record.name || "data-source"}-${index}`
                      }
                      record={record}
                      viewMode={viewMode}
                      permissions={permissions}
                      testingId={testingId}
                      editingId={editingId}
                      onEdit={(item) => void handleEdit(item)}
                      onDelete={handleDelete}
                      onTestConnection={(item) =>
                        void handleTestConnection(item)
                      }
                    />
                  ))}
                </motion.section>

                {!loading && records.length === 0 ? (
                  <div className="mt-6">
                    <DataSourceEmptyState
                      filtered={hasActiveFilters}
                      canCreate={permissions.canCreate}
                      onReset={resetFilters}
                      onCreate={handleCreate}
                    />
                  </div>
                ) : null}
              </div>

              {pagination.total > 0 ? (
                <motion.footer
                  variants={PAGE_ANIMATION.fadeUp}
                  className="mt-auto flex shrink-0 justify-end pt-6"
                >
                  <Pagination
                    page={pagination.pageNo}
                    pageSize={pagination.pageSize}
                    total={pagination.total}
                    showSizeChanger
                    showQuickJumper
                    pageSizeOptions={DATA_SOURCE_PAGE_SIZE_OPTIONS}
                    disabled={loading}
                    renderTotal={(total, range) =>
                      intl.formatMessage(
                        { id: "pages.datasource.pagination.total" },
                        { start: range[0], end: range[1], total },
                      )
                    }
                    onChange={changePage}
                  />
                </motion.footer>
              ) : null}
            </div>
          </motion.section>
        </motion.main>
      </div>

      <DataSourceEditor ref={modalRef} />

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !deleting) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold text-[#161823]">
            {intl.formatMessage({ id: "pages.datasource.delete.confirmTitle" })}
          </DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            {intl.formatMessage(
              { id: "pages.datasource.delete.content" },
              { name: pendingDelete?.name || "-" },
            )}
            <br />
            {intl.formatMessage({ id: "pages.datasource.delete.warning" })}
          </DialogDescription>

          <div className="mt-6 flex justify-end gap-2">
            <Button
              disabled={deleting}
              onClick={() => setPendingDelete(undefined)}
            >
              {intl.formatMessage({
                id: "pages.datasource.delete.cancelText",
              })}
            </Button>
            <Button
              variant="danger"
              loading={deleting}
              onClick={() => void confirmDelete()}
            >
              {intl.formatMessage({ id: "pages.datasource.delete.okText" })}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default DataSourceManagement;
