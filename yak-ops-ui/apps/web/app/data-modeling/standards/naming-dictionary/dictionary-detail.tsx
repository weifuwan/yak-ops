import { BookOpen, LoaderCircle, Pencil, Trash2 } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import { DescriptionItem, SectionTitle, STANDARD_DETAIL_CARD_CLASS } from "../components/detail-fields";
import type { DictionaryStandardRecord } from "./types";

interface Props {
  selectedId: string; selectedRecord?: DictionaryStandardRecord;
  loading: boolean; error: boolean; opening: boolean;
  onRetry: () => void; onBack: () => void; onEdit: () => void;
  onDelete: (record: DictionaryStandardRecord) => void;
}

export function DictionaryDetailPanel({
  selectedId, selectedRecord, loading, error, opening, onRetry, onBack, onEdit, onDelete,
}: Props) {
  if (!selectedId) return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
        <div className="flex flex-col items-center gap-2 text-center">
          <BookOpen size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />
          <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧命名词典查看详情</span>
        </div>
      </div>
    </main>
  );
  if (loading) return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
        <LoaderCircle size={15} className="mr-2 animate-spin" />正在加载命名词典详情...
      </div>
    </main>
  );
  if (error || !selectedRecord) return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-white text-xs text-[var(--yak-components-muted-text)]">
        <span>命名词典详情加载失败</span>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={onRetry}>重试</Button>
          <Button size="sm" variant="ghost" onClick={onBack}>返回列表</Button>
        </div>
      </div>
    </main>
  );

  const isSystem = selectedRecord.flag === 1;
  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <header className="flex h-[72px] shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] bg-white px-5">
        <div className="flex min-w-0 items-center gap-2">
          <BookOpen size={20} className="shrink-0 text-[var(--yak-color-primary)]" />
          <div className="min-w-0">
            <h1 className="truncate text-base font-semibold text-[var(--yak-components-panel-text)]">{selectedRecord.cnName || selectedRecord.enShortName || "命名词典"}</h1>
            <p className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">{selectedRecord.enShortName || "-"} · {selectedRecord.enName || "-"}</p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {isSystem ? <span className="mr-1 rounded bg-[#f2f4f7] px-2 py-1 text-[10px] text-[#667085]">系统内置</span> : null}
          <Button size="sm" variant="ghost" className="text-xs" isDisabled={isSystem || opening} onClick={onEdit}><Pencil size={13} />编辑</Button>
          <Button size="sm" variant="ghost" className="text-xs text-[#d92d20]" isDisabled={isSystem}
            onClick={() => onDelete(selectedRecord)}><Trash2 size={13} />删除</Button>
        </div>
      </header>
      <div className="min-h-0 flex-1 overflow-y-auto bg-[#f6f6f6] p-4">
        <section className={STANDARD_DETAIL_CARD_CLASS}>
          <div className="mb-4"><SectionTitle>基本信息</SectionTitle></div>
          <div className="grid grid-cols-1 gap-x-8 gap-y-3 px-2 sm:grid-cols-2 xl:grid-cols-3">
            <DescriptionItem label="英文缩写" value={selectedRecord.enShortName} />
            <DescriptionItem label="英文名称" value={selectedRecord.enName} />
            <DescriptionItem label="中文名称" value={selectedRecord.cnName} />
            <DescriptionItem label="创建人" value={selectedRecord.createBy} />
            <DescriptionItem label="创建时间" value={selectedRecord.createTime} />
            <DescriptionItem label="修改人" value={selectedRecord.updateBy} />
            <DescriptionItem label="修改时间" value={selectedRecord.updateTime} />
            <DescriptionItem label="类型" value={isSystem ? "系统内置" : "自定义"} />
          </div>
        </section>
      </div>
    </main>
  );
}

export default DictionaryDetailPanel;
