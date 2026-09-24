import { useIntl } from "../i18n";
import { message, Modal, Pagination, Spin } from "antd";
import { motion } from "framer-motion";
import { useRef } from "react";

import AddOrEditDataSourceModal from "../editor/DataSourceEditor";
import DataSourceCard from "./DataSourceCard";
import DataSourceEmptyState from "./DataSourceEmptyState";
import DataSourcePageHeader from "./DataSourcePageHeader";
import DataSourceSummaryCards from "./DataSourceSummaryCards";
import DataSourceToolbar from "./DataSourceToolbar";
import { DATA_SOURCE_PAGE_SIZE_OPTIONS, PAGE_ANIMATION } from "../model/constants";
import { useDataSourcePage } from "./useDataSourceManagement";
import type { DataSourceModalRef, DataSourceRecord } from "../model/uiTypes";
import { DataSourceOperateType, dataSourceRecordKey } from "./types";

const { confirm } = Modal;

const DataSourceManagement = () => {
  const intl = useIntl();
  const modalRef = useRef<DataSourceModalRef>(null);

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
      // The shared request layer owns request error feedback.
    }
  };

  const handleDelete = (record: DataSourceRecord) => {
    if (!permissions.canDelete) return;

    confirm({
      title: intl.formatMessage({ id: "pages.datasource.delete.confirmTitle" }),
      centered: true,
      content: (
        <span>
          {intl.formatMessage(
            { id: "pages.datasource.delete.content" },
            { name: record.name || "-" }
          )}
          <br />
          {intl.formatMessage({ id: "pages.datasource.delete.warning" })}
        </span>
      ),
      okText: intl.formatMessage({ id: "pages.datasource.delete.okText" }),
      cancelText: intl.formatMessage({
        id: "pages.datasource.delete.cancelText",
      }),
      okType: "primary",
      okButtonProps: { size: "small", danger: true },
      cancelButtonProps: { size: "small" },
      maskClosable: true,
      async onOk() {
        if (record.id === undefined || record.id === null) {
          message.error(
            intl.formatMessage({ id: "pages.datasource.delete.idMissing" })
          );
          return;
        }

        try {
          const deleted = await removeRecord(record.id);
          if (deleted) {
            message.success(
              intl.formatMessage({ id: "pages.datasource.delete.success" })
            );
          }
        } catch {
          // The shared request layer owns request error feedback.
        }
      },
    });
  };

  const handleTestConnection = async (record: DataSourceRecord) => {
    try {
      const connected = await testRecord(record);
      if (connected) {
        message.success(
          intl.formatMessage({ id: "pages.datasource.test.success" })
        );
      }
    } catch {
      // The shared request layer owns request error feedback.
    }
  };

  return (
    <>
      <div className="min-h-[calc(100dvh-64px)] bg-[#f7f8fa] text-[#242731]">
        <motion.main
          initial="hidden"
          animate="visible"
          variants={PAGE_ANIMATION.sectionStagger}
          // className="px-4 pb-4 pt-4"
        >
          <motion.section
            variants={PAGE_ANIMATION.fadeUp}
            className="flex min-h-[calc(100dvh-64px)] flex-col 
             bg-white px-6 pb-4 pt-5 shadow-[0_2px_10px_rgba(31,35,41,0.025)] max-md:px-4"
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
              <Spin spinning={loading}>
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
              </Spin>

              {pagination.total > 0 ? (
                <motion.footer
                  variants={PAGE_ANIMATION.fadeUp}
                  className="mt-auto flex shrink-0 justify-end pt-6"
                >
                  <Pagination
                    current={pagination.pageNo}
                    pageSize={pagination.pageSize}
                    total={pagination.total}
                    showSizeChanger
                    showQuickJumper
                    pageSizeOptions={DATA_SOURCE_PAGE_SIZE_OPTIONS}
                    disabled={loading}
                    showTotal={(total, range) =>
                      intl.formatMessage(
                        { id: "pages.datasource.pagination.total" },
                        { start: range[0], end: range[1], total }
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

      <AddOrEditDataSourceModal ref={modalRef} />
    </>
  );
};

export default DataSourceManagement;
