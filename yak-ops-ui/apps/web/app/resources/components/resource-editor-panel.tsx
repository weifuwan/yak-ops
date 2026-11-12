import { useState } from "react";
import {
  ArrowLeft,
  Check,
  FileCode2,
  LoaderCircle,
  Pencil,
  Save,
} from "lucide-react";
import { Button, Textarea } from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import type { ResourceEditorMode } from "../types";
import { getExtension, getName } from "../utils";

interface ResourceEditorPanelProps {
  mode: ResourceEditorMode;
  item: ResourceItemRecord | null;
  content: string;
  loading: boolean;
  saving: boolean;
  dirty: boolean;
  error: string;
  onContentChange: (value: string) => void;
  onSave: () => void;
  onClose: () => void;
  onRetry: () => void;
  onSwitchToEdit: () => void;
}

export function ResourceEditorPanel({
  mode,
  item,
  content,
  loading,
  saving,
  dirty,
  error,
  onContentChange,
  onSave,
  onClose,
  onRetry,
  onSwitchToEdit,
}: ResourceEditorPanelProps) {
  const [scrollTop, setScrollTop] = useState(0);
  const preview = mode === "preview";
  const title = item ? getName(item) : "资源文件";
  const extension = getExtension(title).toUpperCase() || "TEXT";
  const lineCount = Math.max(1, content.split("\n").length);

  return (
    <section className="flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden rounded-lg border border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex min-h-[68px] shrink-0 items-center justify-between gap-4 border-b border-[#edf0f3] px-4">
        <div className="flex min-w-0 items-center gap-3">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            isIconOnly
            aria-label="返回资源列表"
            title="返回资源列表"
            className="size-8 shrink-0"
            onClick={onClose}
          >
            <ArrowLeft size={16} />
          </Button>

          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-[#edf0f3] bg-[#fafbfc] text-[#697586]">
            <FileCode2 size={17} strokeWidth={1.7} />
          </span>

          <div className="min-w-0">
            <div className="flex min-w-0 items-center gap-2">
              <h2 className="truncate text-[13px] font-semibold text-[#303846]" title={title}>
                {title}
              </h2>
              <span className="shrink-0 rounded bg-[#f3f5f8] px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-[#778294]">
                {extension}
              </span>
              {!preview && dirty ? (
                <span className="inline-flex shrink-0 items-center gap-1 text-[10px] text-[#c17b25]">
                  <span className="size-1.5 rounded-full bg-[#d99a45]" />
                  未保存
                </span>
              ) : null}
            </div>
            <div
              className="mt-1 max-w-[52vw] truncate text-[11px] text-[#9aa2ad]"
              title={item?.fullName}
            >
              {item?.fullName || "资源路径"}
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {preview ? (
            <>
              <span className="hidden text-[11px] text-[#929baa] sm:inline">
                只读预览
              </span>
              <Button size="sm" variant="primary" onClick={onSwitchToEdit}>
                <Pencil size={13} />
                在线编辑
              </Button>
            </>
          ) : (
            <>
              {!dirty ? (
                <span className="hidden items-center gap-1 text-[11px] text-[#8a96a5] sm:inline-flex">
                  <Check size={13} />
                  已保存
                </span>
              ) : null}
              <Button
                size="sm"
                variant="primary"
                isPending={saving}
                isDisabled={loading || saving || !dirty || !content.length}
                onClick={onSave}
              >
                <Save size={13} />
                保存
              </Button>
            </>
          )}
        </div>
      </header>

      <div className="flex h-10 shrink-0 items-center justify-between border-b border-[#edf0f3] bg-[#fafbfc] px-4">
        <div className="flex items-center gap-2.5 text-[11px] text-[#8b95a3]">
          <span className="rounded border border-[#e8ebef] bg-white px-1.5 py-0.5 font-medium text-[#687386]">
            {extension}
          </span>
          <span>UTF-8</span>
          <span className="text-[#c4cad2]">·</span>
          <span>纯文本</span>
        </div>
        <div className="text-[11px] tabular-nums text-[#9aa2ad]">
          {lineCount.toLocaleString("zh-CN")} 行
          <span className="mx-1.5 text-[#c4cad2]">·</span>
          {content.length.toLocaleString("zh-CN")} 字符
        </div>
      </div>

      {error ? (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 px-5 text-center">
          <span className="flex size-11 items-center justify-center rounded-full bg-[#f5f6f8] text-[#929baa]">
            <FileCode2 size={21} strokeWidth={1.5} />
          </span>
          <div>
            <div className="text-sm font-medium text-[#394150]">文件内容加载失败</div>
            <div className="mt-1 max-w-[560px] break-all text-xs leading-5 text-[#929baa]">
              {error}
            </div>
          </div>
          <Button size="sm" variant="secondary" onClick={onRetry}>
            重试
          </Button>
        </div>
      ) : loading ? (
        <div className="flex min-h-0 flex-1 items-center justify-center gap-2 text-xs text-[#8b94a2]">
          <LoaderCircle size={15} className="animate-spin" />
          正在读取文件内容
        </div>
      ) : (
        <div className="flex min-h-0 flex-1 overflow-hidden bg-[#fcfcfd]">
          <div className="w-12 shrink-0 select-none overflow-hidden border-r border-[#f0f2f5] bg-[#fafbfc] pt-4 text-right font-mono text-[11px] leading-6 tabular-nums text-[#b7bec8]">
            <div style={{ transform: "translateY(-" + scrollTop + "px)" }}>
              {Array.from({ length: lineCount }, (_, index) => (
                <div key={index} className="h-6 px-3">
                  {index + 1}
                </div>
              ))}
            </div>
          </div>

          <Textarea
            aria-label={preview ? "文件内容预览" : "文件内容编辑器"}
            value={content}
            readOnly={preview || saving}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            onValueChange={(value) => onContentChange(String(value ?? ""))}
            onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
            className={[
              "h-full min-h-0 w-0 min-w-0 flex-1 resize-none rounded-none border-0 bg-[#fcfcfd] px-5 pb-6 pt-4 font-mono text-[12px] leading-6 text-[#303846] shadow-none outline-none",
              "whitespace-pre focus:border-transparent focus:bg-[#fcfcfd] focus:ring-0 hover:border-transparent hover:bg-[#fcfcfd]",
              preview ? "cursor-default" : "cursor-text",
            ].join(" ")}
          />
        </div>
      )}

      <footer className="flex h-8 shrink-0 items-center justify-between border-t border-[#edf0f3] bg-[#fafbfc] px-4 text-[10px] text-[#9aa2ad]">
        <span>{preview ? "预览模式 · 只读" : "编辑模式"}</span>
        <span>资源文件</span>
      </footer>
    </section>
  );
}
