import { ChevronRight, FileText, FolderOpen, Layers, LoaderCircle, Pencil, Plus } from "lucide-react";
import { Button, Input, Table, type TableColumns } from "@yak-ops/yak-ui";

import type { StandardDirectoryRecord, StandardModule, StandardRecord, StandardTreeNode } from "../types";
import { getDirectoryTypeLabel } from "../types";
import { DescriptionItem, SectionTitle, STANDARD_DETAIL_CARD_CLASS } from "./detail-fields";
import { StandardRecordDetails } from "./standard-record-detail";

interface Props {
  module: StandardModule; title: string; selectedNode?: StandardTreeNode; selectedIsDirectory: boolean;
  detailLoading: boolean; detailError: boolean;
  selectedDirectory?: StandardDirectoryRecord; selectedRecord?: StandardRecord; breadcrumb: string[];
  directoryItems: StandardDirectoryRecord[]; directoryTotal: number; directoryLoading: boolean;
  directoryPageNo: number; onDirectoryPageChange: (page: number) => void;
  directoryColumns: TableColumns<StandardDirectoryRecord>;
  recordItems: StandardRecord[]; recordTotal: number; recordLoading: boolean;
  recordPageNo: number; onRecordPageChange: (page: number) => void;
  recordKeyword: string; onRecordKeywordChange: (value: string) => void;
  recordColumns: TableColumns<StandardRecord>;
  onRetry: () => void; onBack: () => void;
  onCreateDirectory: (parentId?: string) => void; onCreateStandardSet: (parentId: string) => void;
  onCreateRecord: (parentId: string) => void; onEditDirectory: (id: string) => void; onEditRecord: (id: string) => void;
}
const PAGE_SIZE = 10;

export function DirectoryStandardsDetail({
  module, title, selectedNode, selectedIsDirectory, detailLoading, detailError,
  selectedDirectory, selectedRecord, breadcrumb, directoryItems, directoryTotal,
  directoryLoading, directoryPageNo, onDirectoryPageChange, directoryColumns,
  recordItems, recordTotal, recordLoading, recordPageNo, onRecordPageChange,
  recordKeyword, onRecordKeywordChange, recordColumns, onRetry, onBack,
  onCreateDirectory, onCreateStandardSet, onCreateRecord, onEditDirectory, onEditRecord,
}: Props) {
  if (!selectedNode) return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <div className="flex flex-col items-center gap-2 text-center">
          <Layers size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />
          <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧目录或标准查看详情</span>
        </div>
      </div>
    </section>
  );
  if (detailLoading) return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
        <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载详情...
      </div>
    </section>
  );
  if (detailError || (!selectedDirectory && !selectedRecord)) return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
        <span>详情加载失败</span>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          <Button size="sm" variant="ghost" onClick={onBack}>返回目录</Button>
        </div>
      </div>
    </section>
  );

  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden">
      <header className="flex h-[72px] shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] bg-white px-5">
        <div className="flex min-w-0 items-center gap-2">
          {selectedIsDirectory ? <FolderOpen size={20} className="shrink-0 text-[var(--yak-color-primary)]" /> : <FileText size={20} className="shrink-0 text-[var(--yak-color-primary)]" />}
          <div className="min-w-0">
            <h1 className="truncate text-base font-semibold text-[var(--yak-components-panel-text)]">{selectedNode.title}</h1>
            <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[11px] text-[var(--yak-components-muted-text)]">
              <span>{title}</span>
              {breadcrumb.map((part, index) => <span key={part + index} className="flex items-center gap-1"><ChevronRight size={11} />{part}</span>)}
            </div>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {selectedIsDirectory && selectedNode.type === "FOLDER" ? (
            <>
              {module === "FIELD" ? <Button size="sm" variant="secondary" onClick={() => onCreateStandardSet(selectedNode.id)}>新建标准集</Button> : null}
              <Button size="sm" variant="primary" onClick={() => onCreateRecord(selectedNode.id)}>
                <Plus size={13} />新建{module === "FIELD" ? "字段标准" : module === "CODE" ? "标准代码" : "度量单位"}
              </Button>
            </>
          ) : null}
          {selectedIsDirectory && selectedNode.type === "STANDARD_SET" ? (
            <Button size="sm" variant="primary" onClick={() => onCreateRecord(selectedNode.id)}><Plus size={13} />新建字段标准</Button>
          ) : null}
          <Button size="sm" variant="ghost" className="text-xs"
            onClick={() => selectedIsDirectory ? onEditDirectory(selectedNode.id) : onEditRecord(selectedNode.id)}>
            <Pencil size={13} />编辑
          </Button>
        </div>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto bg-[#f6f6f6] p-4">
        {selectedIsDirectory ? (
          <>
            <section className={STANDARD_DETAIL_CARD_CLASS}>
              <div className="mb-3 flex items-center justify-between"><SectionTitle>目录信息</SectionTitle></div>
              <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-2 sm:grid-cols-2 xl:grid-cols-3">
                <DescriptionItem label="目录名称" value={selectedDirectory?.dirName} />
                <DescriptionItem label="目录类型" value={getDirectoryTypeLabel(selectedDirectory?.dirType)} />
                <DescriptionItem label="所属模块" value={title} />
                <DescriptionItem label="创建人" value={selectedDirectory?.createBy} />
                <DescriptionItem label="创建时间" value={selectedDirectory?.createTime} />
                <DescriptionItem label="修改人" value={selectedDirectory?.updateBy} />
                <DescriptionItem label="修改时间" value={selectedDirectory?.updateTime} />
              </div>
            </section>
            {selectedNode.type === "FOLDER" ? (
              <section className={STANDARD_DETAIL_CARD_CLASS}>
                <div className="mb-3 flex items-center justify-between">
                  <SectionTitle>子目录</SectionTitle>
                  <Button size="sm" variant="secondary" onClick={() => onCreateDirectory(selectedNode.id)}><Plus size={13} />新建子目录</Button>
                </div>
                <Table<StandardDirectoryRecord> columns={directoryColumns} dataSource={directoryItems}
                  rowKey={(item) => item.id || ""} loading={directoryLoading} bordered size="small" emptyText="暂无子目录"
                  pagination={directoryTotal > 0 ? {
                    current: directoryPageNo, pageSize: PAGE_SIZE, total: directoryTotal,
                    disabled: directoryLoading, onChange: onDirectoryPageChange,
                  } : false} />
              </section>
            ) : null}
            <section className={STANDARD_DETAIL_CARD_CLASS}>
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <SectionTitle>{module === "FIELD" ? "字段标准列表" : module === "CODE" ? "标准代码列表" : "度量单位列表"}</SectionTitle>
                <div className="flex items-center gap-2">
                  <Input size="small" variant="outlined" value={recordKeyword}
                    placeholder={"搜索" + title} onChange={(event) => onRecordKeywordChange(event.target.value)} />
                  <Button size="sm" variant="primary" onClick={() => onCreateRecord(selectedNode.id)}><Plus size={13} />新增</Button>
                </div>
              </div>
              <Table<StandardRecord> columns={recordColumns} dataSource={recordItems}
                rowKey={(item) => item.id || ""} loading={recordLoading} bordered size="small" emptyText={"暂无" + title}
                pagination={recordTotal > 0 ? {
                  current: recordPageNo, pageSize: PAGE_SIZE, total: recordTotal,
                  disabled: recordLoading, onChange: onRecordPageChange,
                } : false} />
            </section>
          </>
        ) : (
          <StandardRecordDetails module={module} record={selectedRecord!}
            directoryName={breadcrumb.length > 1 ? breadcrumb[breadcrumb.length - 2] : undefined} />
        )}
      </div>
    </section>
  );
}

export default DirectoryStandardsDetail;
