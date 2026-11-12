import { useRef, useState, type ChangeEvent, type MouseEvent } from "react";
import { FolderOpen, LoaderCircle } from "lucide-react";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Pagination,
} from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import { ResourceBreadcrumb } from "./components/resource-breadcrumb";
import { ResourceContextMenu } from "./components/resource-context-menu";
import { ResourceFileEditorModal } from "./components/resource-file-editor-modal";
import { ResourceDetailsPanel } from "./components/resource-details-panel";
import { ResourceDialog } from "./components/resource-dialog";
import { ResourceGrid } from "./components/resource-grid";
import { ResourceHeader } from "./components/resource-header";
import { ResourceTable } from "./components/resource-table";
import { ResourceToolbar } from "./components/resource-toolbar";
import { useResourceManagement } from "./hooks/use-resource-management";
import { canEditResource } from "./utils";

export function ResourceManagementPage() {
  const resource = useResourceManagement();
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [discardEditorOpen, setDiscardEditorOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<{
    item: ResourceItemRecord;
    x: number;
    y: number;
  } | null>(null);
  const replaceInputRef = useRef<HTMLInputElement | null>(null);
  const replaceTargetRef = useRef<ResourceItemRecord | null>(null);

  const handleContextMenu = (
    item: ResourceItemRecord,
    event: MouseEvent<HTMLElement>,
  ) => {
    event.preventDefault();
    if (!resource.selectedFullNames.has(item.fullName)) {
      resource.selectItem(item);
    }
    setContextMenu({
      item,
      x: Math.min(event.clientX, Math.max(8, window.innerWidth - 190)),
      y: Math.min(event.clientY, Math.max(8, window.innerHeight - 310)),
    });
  };

  const handleOpenFile = (item: ResourceItemRecord) => {
    if (canEditResource(item)) {
      void resource.openResourceFile(item);
    } else {
      void resource.downloadFile(item);
    }
  };

  const handleReplaceRequest = (item: ResourceItemRecord) => {
    replaceTargetRef.current = item;
    replaceInputRef.current?.click();
  };

  const handleReplaceChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    const target = replaceTargetRef.current;
    replaceTargetRef.current = null;
    event.currentTarget.value = "";
    if (file && target) void resource.replaceFile(target, file);
  };

  const handleCloseEditor = () => {
    if (resource.isEditorDirty) {
      setDiscardEditorOpen(true);
      return;
    }
    resource.closeResourceEditor();
  };

  const confirmCloseEditor = () => {
    setDiscardEditorOpen(false);
    resource.closeResourceEditor();
  };

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
          busy={
            resource.actionPending ||
            resource.editorSaving ||
            resource.createFileSaving
          }
          uploading={resource.uploading}
          onCreateFolder={resource.openCreateDialog}
          onCreateFile={resource.openCreateFile}
          onUpload={resource.uploadFiles}
        />

        <main className="flex min-h-0 flex-1 flex-col px-7 pb-5 pt-5 max-md:px-4">
          <>
              <ResourceToolbar
                keyword={resource.keyword}
                onKeywordChange={resource.handleKeywordChange}
                onClearKeyword={resource.clearKeyword}
                typeFilter={resource.typeFilter}
                onTypeFilterChange={resource.handleTypeFilterChange}
                sortBy={resource.sortBy}
                onSortChange={resource.setSortBy}
                viewMode={resource.viewMode}
                onViewModeChange={resource.setViewMode}
                detailsOpen={detailsOpen}
                onToggleDetails={() => setDetailsOpen((open) => !open)}
                onRefresh={() => void resource.refresh()}
                isLoading={resource.isLoading}
                isFetching={resource.isFetching}
              />

              <ResourceBreadcrumb
                path={resource.directoryPath}
                total={resource.total}
                canGoParent={resource.canGoParent}
                onParentClick={resource.openParentDirectory}
                onRootClick={resource.openRootDirectory}
                onOpen={resource.openBreadcrumb}
              />
          </>

          <div className="flex min-h-0 flex-1 gap-3">
            <>
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
                        onSelect={resource.selectItem}
                        onContextMenu={handleContextMenu}
                        onOpen={resource.openDirectory}
                        onOpenFile={handleOpenFile}
                        onEdit={(item) => void resource.openResourceFile(item)}
                        onDownload={resource.downloadFile}
                        onReplace={handleReplaceRequest}
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
                      onSelect={resource.selectItem}
                      onContextMenu={handleContextMenu}
                      onOpen={resource.openDirectory}
                      onOpenFile={handleOpenFile}
                      onEdit={(item) => void resource.openResourceFile(item)}
                      onDownload={resource.downloadFile}
                      onReplace={handleReplaceRequest}
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

                {detailsOpen ? (
                  <ResourceDetailsPanel
                    items={resource.selectedItems}
                    onClose={() => setDetailsOpen(false)}
                    onCopyPath={resource.copyResourcePath}
                  />
                ) : null}
            </>
          </div>
        </main>
      </div>

      <input
        ref={replaceInputRef}
        type="file"
        className="hidden"
        onChange={handleReplaceChange}
      />

      <ResourceContextMenu
        item={contextMenu?.item ?? null}
        position={contextMenu ? { x: contextMenu.x, y: contextMenu.y } : null}
        onClose={() => setContextMenu(null)}
        onOpen={(item) => {
          setContextMenu(null);
          resource.openDirectory(item);
        }}
        onEdit={(item) => {
          setContextMenu(null);
          void resource.openResourceFile(item);
        }}
        onDownload={(item) => {
          setContextMenu(null);
          void resource.downloadFile(item);
        }}
        onReplace={(item) => {
          setContextMenu(null);
          handleReplaceRequest(item);
        }}
        onRename={(item) => {
          setContextMenu(null);
          resource.openRenameDialog(item);
        }}
        onDelete={(item) => {
          setContextMenu(null);
          resource.openDeleteDialog(item);
        }}
        onCopyPath={(item) => {
          setContextMenu(null);
          void resource.copyResourcePath(item);
        }}
        onShowDetails={() => {
          setContextMenu(null);
          setDetailsOpen(true);
        }}
      />

      <ResourceDialog
        dialog={resource.dialog}
        value={resource.dialogValue}
        pending={resource.actionPending}
        onValueChange={resource.setDialogValue}
        onConfirm={resource.confirmDialog}
        onClose={resource.closeDialog}
      />

      <ResourceFileEditorModal
        mode="create"
        open={resource.createFileOpen}
        currentDirectory={resource.currentDir}
        fileName={resource.createFileName}
        suffix={resource.createFileSuffix}
        content={resource.createFileContent}
        saving={resource.createFileSaving}
        onFileNameChange={resource.setCreateFileName}
        onSuffixChange={resource.setCreateFileSuffix}
        onContentChange={resource.setCreateFileContent}
        onSubmit={() => void resource.saveNewResourceFile()}
        onClose={resource.closeCreateFileDialog}
      />

      <ResourceFileEditorModal
        mode="edit"
        open={Boolean(resource.editorItem)}
        currentDirectory={resource.currentDir}
        item={resource.editorItem}
        fileName=""
        suffix=""
        content={resource.editorContent}
        loading={resource.editorLoading}
        saving={resource.editorSaving}
        dirty={resource.isEditorDirty}
        error={resource.editorError}
        onContentChange={resource.setEditorContent}
        onSubmit={() => void resource.saveResourceEditor()}
        onClose={handleCloseEditor}
        onRetry={() => {
          if (resource.editorItem) {
            void resource.openResourceFile(resource.editorItem);
          }
        }}
      />

      <Dialog
        open={discardEditorOpen}
        onOpenChange={setDiscardEditorOpen}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">
            放弃未保存的修改？
          </DialogTitle>
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            当前文件还有未保存的内容，确定离开并丢弃这些修改吗？
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => setDiscardEditorOpen(false)}
            >
              继续编辑
            </Button>
            <Button size="sm" variant="danger" onClick={confirmCloseEditor}>
              放弃修改
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default ResourceManagementPage;
