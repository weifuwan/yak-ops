import { FolderOpen, LoaderCircle } from "lucide-react";
import { Button, Pagination } from "@yak-ops/yak-ui";

import { ResourceBreadcrumb } from "./components/resource-breadcrumb";
import { ResourceDialog } from "./components/resource-dialog";
import { ResourceGrid } from "./components/resource-grid";
import { ResourceHeader } from "./components/resource-header";
import { ResourceTable } from "./components/resource-table";
import { ResourceToolbar } from "./components/resource-toolbar";
import { useResourceManagement } from "./hooks/use-resource-management";

export function ResourceManagementPage() {
  const resource = useResourceManagement();

  const emptyState = (
    <div className="flex min-h-[220px] flex-col items-center justify-center gap-2 text-[13px] text-[var(--yak-components-muted-text)]">
      <FolderOpen size={34} strokeWidth={1.3} className="text-[#c4cbd4]" />
      <span>
        {resource.deferredKeyword
          ? "没有找到匹配的资源"
          : resource.typeFilter !== "ALL"
            ? "当前目录没有符合类型的资源"
            : "当前目录暂无资源"}
      </span>
      <span className="text-[11px] text-[#a0a7b2]">
        上传文件或新建文件夹，开始管理资源
      </span>
    </div>
  );

  return (
    <>
      <div className="flex min-h-full flex-col bg-white text-[#252b35]">
        <ResourceHeader
          canManage={Boolean(resource.currentDir)}
          busy={resource.actionPending}
          uploading={resource.uploading}
          onCreateFolder={resource.openCreateDialog}
          onUpload={resource.uploadFiles}
        />

        <main className="flex min-h-0 flex-1 flex-col px-7 pb-5 pt-5 max-md:px-4">
          <ResourceToolbar
            keyword={resource.keyword}
            onKeywordChange={resource.handleKeywordChange}
            onClearKeyword={resource.clearKeyword}
            selectedCount={resource.selectedFullNames.size}
            canDeleteSelected={resource.selectedItems.length > 0}
            onClearSelection={resource.clearSelection}
            onDeleteSelected={resource.openDeleteSelectedDialog}
            typeFilter={resource.typeFilter}
            onTypeFilterChange={resource.handleTypeFilterChange}
            sortBy={resource.sortBy}
            onSortChange={resource.setSortBy}
            viewMode={resource.viewMode}
            onViewModeChange={resource.setViewMode}
            onRefresh={() => void resource.refresh()}
            isLoading={resource.isLoading}
            isFetching={resource.isFetching}
          />

          <ResourceBreadcrumb
            path={resource.directoryPath}
            total={resource.total}
            onRootClick={resource.openRootDirectory}
            onOpen={resource.openBreadcrumb}
          />

          <section className="flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden rounded-lg border border-[var(--yak-components-panel-border)] bg-white">
            {resource.isTreeError || resource.isBaseDirError ? (
              <div className="flex flex-1 items-center justify-center gap-3 text-[13px] text-[var(--yak-components-muted-text)]">
                资源目录加载失败
                <Button size="sm" variant="secondary" onClick={() => void resource.refresh()}>
                  重试
                </Button>
              </div>
            ) : resource.isItemsError ? (
              <div className="flex flex-1 items-center justify-center gap-3 text-[13px] text-[var(--yak-components-muted-text)]">
                文件列表加载失败
                <Button size="sm" variant="secondary" onClick={() => void resource.retryItems()}>
                  重试
                </Button>
              </div>
            ) : resource.isLoading && resource.visibleItems.length === 0 ? (
              <div className="flex flex-1 items-center justify-center gap-2 text-[13px] text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={16} className="animate-spin" />
                正在加载资源
              </div>
            ) : resource.viewMode === "grid" ? (
              resource.visibleItems.length === 0 ? (
                <div className="flex flex-1 flex-col items-center justify-center gap-2 text-[13px] text-[var(--yak-components-muted-text)]">
                  <FolderOpen size={34} strokeWidth={1.3} className="text-[#c4cbd4]" />
                  <span>
                    {resource.deferredKeyword
                      ? "没有找到匹配的资源"
                      : resource.typeFilter !== "ALL"
                        ? "当前目录没有符合类型的资源"
                        : "当前目录暂无资源"}
                  </span>
                  <span className="text-[11px] text-[#b0b7c1]">
                    上传文件或新建文件夹，开始管理资源
                  </span>
                </div>
              ) : (
                <ResourceGrid
                  items={resource.visibleItems}
                  selectedKeys={resource.selectedFullNames}
                  onToggleSelection={resource.toggleItemSelection}
                  onOpen={resource.openDirectory}
                  onDownload={resource.downloadFile}
                  onRename={resource.openRenameDialog}
                  onDelete={resource.openDeleteDialog}
                />
              )
            ) : (
              <ResourceTable
                items={resource.visibleItems}
                selectedKeys={resource.selectedFullNames}
                loading={resource.isFetching}
                emptyText={emptyState}
                onSelectionChange={resource.handleSelectionChange}
                onOpen={resource.openDirectory}
                onDownload={resource.downloadFile}
                onRename={resource.openRenameDialog}
                onDelete={resource.openDeleteDialog}
              />
            )}

            {resource.total > 0 ? (
              <footer className="flex min-h-[56px] shrink-0 items-center justify-end border-t border-[var(--yak-components-panel-border)] px-4 py-2">
                <Pagination
                  page={resource.pageNo}
                  pageSize={resource.pageSize}
                  total={resource.total}
                  pageSizeOptions={[10, 20, 50]}
                  pageSizeLabel="每页"
                  showSizeChanger
                  disabled={resource.isFetching}
                  renderTotal={(count, [start, end]) => (
                    <span className="tabular-nums">
                      显示 {start}-{end} 条，共 {count} 条
                    </span>
                  )}
                  onChange={resource.handlePageChange}
                />
              </footer>
            ) : null}
          </section>
        </main>
      </div>

      <ResourceDialog
        dialog={resource.dialog}
        value={resource.dialogValue}
        pending={resource.actionPending}
        onValueChange={resource.setDialogValue}
        onConfirm={resource.confirmDialog}
        onClose={resource.closeDialog}
      />
    </>
  );
}

export default ResourceManagementPage;
