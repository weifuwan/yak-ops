import { AlertCircle, FileX, FolderOpen, FolderX, LoaderCircle } from "lucide-react";
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
            {resource.isTreeError && !resource.isBaseDirError ? (
              <div className="mx-4 mt-3 flex shrink-0 items-center gap-2 rounded-md border border-[#f0e8d8] bg-[#fffdf8] px-3 py-2 text-[12px] text-[#8a6b35]">
                <AlertCircle size={14} className="shrink-0" />
                <span className="min-w-0 flex-1">目录结构暂时无法读取，当前文件列表仍可尝试使用。</span>
                <Button size="sm" variant="secondary" onClick={() => void resource.refresh()}>
                  重试
                </Button>
              </div>
            ) : null}

            {resource.isBaseDirError ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                <AlertCircle size={30} strokeWidth={1.4} className="text-[#a0a7b2]" />
                <strong className="font-medium text-[var(--yak-components-panel-text)]">资源根目录暂时无法读取</strong>
                <span>暂时无法获取资源存储位置，请稍后重试。</span>
                <Button size="sm" variant="secondary" onClick={() => void resource.refresh()}>
                  重试
                </Button>
              </div>
            ) : resource.isCurrentDirectoryMissing ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                <FolderX size={34} strokeWidth={1.3} className="text-[#a0a7b2]" />
                <strong className="font-medium text-[var(--yak-components-panel-text)]">当前文件夹已不存在</strong>
                <span className="max-w-md">该文件夹可能已从本地存储中删除或移动。返回资源根目录后，可以继续管理其他资源。</span>
                <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                  <Button size="sm" variant="secondary" onClick={resource.openRootDirectory}>
                    返回资源根目录
                  </Button>
                  <Button size="sm" onClick={() => void resource.refresh()}>
                    重试
                  </Button>
                </div>
              </div>
            ) : resource.isItemsNotFoundError ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                <FileX size={34} strokeWidth={1.3} className="text-[#a0a7b2]" />
                <strong className="font-medium text-[var(--yak-components-panel-text)]">当前资源路径已不存在</strong>
                <span className="max-w-md">该资源可能已被本地删除或移动。刷新列表后，将重新检查当前资源是否仍然存在。</span>
                <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                  <Button size="sm" variant="secondary" onClick={() => void resource.refresh()}>
                    刷新资源列表
                  </Button>
                  <Button size="sm" onClick={resource.openRootDirectory}>
                    返回资源根目录
                  </Button>
                </div>
              </div>
            ) : resource.isItemsPathInvalidError ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                <AlertCircle size={30} strokeWidth={1.4} className="text-[#a0a7b2]" />
                <strong className="font-medium text-[var(--yak-components-panel-text)]">资源路径无法识别</strong>
                <span className="max-w-md">该资源可能已被删除或移动，也可能是当前存储路径异常。可以刷新列表，或返回资源根目录继续使用。</span>
                <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                  <Button size="sm" variant="secondary" onClick={() => void resource.refresh()}>
                    刷新资源列表
                  </Button>
                  <Button size="sm" onClick={resource.openRootDirectory}>
                    返回资源根目录
                  </Button>
                </div>
              </div>
            ) : resource.isItemsError ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 py-10 text-center text-[13px] text-[var(--yak-components-muted-text)]">
                <AlertCircle size={30} strokeWidth={1.4} className="text-[#a0a7b2]" />
                <strong className="font-medium text-[var(--yak-components-panel-text)]">文件列表暂时无法读取</strong>
                <span className="max-w-md">资源目录中可能存在无法访问的文件，或当前存储路径暂时不可用。可以刷新重试，或返回资源根目录继续使用。</span>
                <div className="flex flex-wrap items-center justify-center gap-2 pt-1">
                  <Button size="sm" variant="secondary" onClick={() => void resource.retryItems()}>
                    重试
                  </Button>
                  <Button size="sm" onClick={resource.openRootDirectory}>
                    返回资源根目录
                  </Button>
                </div>
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
