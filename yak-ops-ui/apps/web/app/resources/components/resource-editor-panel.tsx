import { useState } from "react";
import {
  ArrowLeft,
  FileCode2,
  FilePlus2,
  LoaderCircle,
  Pencil,
  Save,
} from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import type { ResourceEditorMode } from "../types";
import { getExtension, getName } from "../utils";

const FILE_SUFFIX_OPTIONS = [
  { value: "sql", label: "SQL" },
  { value: "py", label: "Python" },
  { value: "sh", label: "Shell" },
  { value: "bat", label: "Batch" },
  { value: "cfg", label: "CFG" },
  { value: "hql", label: "HQL" },
  { value: "ini", label: "INI" },
  { value: "js", label: "JavaScript" },
  { value: "java", label: "Java" },
  { value: "json", label: "JSON" },
  { value: "yaml", label: "YAML" },
  { value: "yml", label: "YML" },
  { value: "xml", label: "XML" },
  { value: "properties", label: "Properties" },
  { value: "txt", label: "TXT" },
  { value: "log", label: "Log" },
  { value: "conf", label: "CONF" },
];

interface ResourceEditorPanelProps {
  mode: ResourceEditorMode;
  item: ResourceItemRecord | null;
  currentDirectory: string;
  fileName: string;
  suffix: string;
  content: string;
  loading: boolean;
  saving: boolean;
  dirty: boolean;
  error: string;
  onFileNameChange: (value: string) => void;
  onSuffixChange: (value: string) => void;
  onContentChange: (value: string) => void;
  onSave: () => void;
  onClose: () => void;
  onRetry: () => void;
  onSwitchToEdit: () => void;
}

export function ResourceEditorPanel({
  mode,
  item,
  currentDirectory,
  fileName,
  suffix,
  content,
  loading,
  saving,
  dirty,
  error,
  onFileNameChange,
  onSuffixChange,
  onContentChange,
  onSave,
  onClose,
  onRetry,
  onSwitchToEdit,
}: ResourceEditorPanelProps) {
  const [scrollTop, setScrollTop] = useState(0);
  const lineCount = Math.max(1, content.split("\n").length);
  const preview = mode === "preview";
  const creating = mode === "create";
  const title = creating ? "新建文件" : item ? getName(item) : "资源文件";
  const language = creating ? suffix.toUpperCase() : getExtension(title).toUpperCase();

  return (
    <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex h-12 shrink-0 items-center justify-between gap-3 border-b border-[#edf0f3] px-3">
        <div className="flex min-w-0 items-center gap-2">
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
          <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-[#f3f5f8] text-[#6f7b8d]">
            {creating ? <FilePlus2 size={15} /> : <FileCode2 size={15} />}
          </span>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#303846]" title={title}>
              {title}
            </div>
            <div
              className="max-w-[50vw] truncate text-[10px] text-[#9aa2ad]"
              title={creating ? currentDirectory : item?.fullName}
            >
              {creating ? "保存位置：" + currentDirectory : item?.fullName}
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          {preview ? (
            <Button size="sm" onClick={onSwitchToEdit}>
              <Pencil size={13} />
              在线编辑
            </Button>
          ) : (
            <>
              <span className="hidden text-[11px] text-[#9aa2ad] sm:inline">
                {dirty ? "有未保存的修改" : "内容已保存"}
              </span>
              <Button
                size="sm"
                isPending={saving}
                isDisabled={loading || saving || !content.length || (creating ? !fileName.trim() : !dirty)}
                onClick={onSave}
              >
                <Save size={13} />
                保存
              </Button>
            </>
          )}
        </div>
      </header>

      {creating ? (
        <div className="flex shrink-0 flex-wrap items-center gap-3 border-b border-[#edf0f3] bg-[#fcfcfd] px-4 py-3">
          <label className="text-xs text-[#7c8797]" htmlFor="resource-new-file-name">
            文件名称
          </label>
          <Input
            id="resource-new-file-name"
            size="small"
            value={fileName}
            onChange={(event) => onFileNameChange(event.target.value)}
            placeholder="例如：daily_sync"
            className="h-8 w-[240px] max-w-full text-xs"
            disabled={saving}
          />
          <label className="text-xs text-[#7c8797]" htmlFor="resource-new-file-suffix">
            文件类型
          </label>
          <select
            id="resource-new-file-suffix"
            value={suffix}
            onChange={(event) => onSuffixChange(event.target.value)}
            disabled={saving}
            className="h-8 min-w-[130px] cursor-pointer rounded-md border border-[var(--yak-components-control-border)] bg-white px-2 text-xs text-[#394150] outline-none focus:border-[var(--yak-color-primary)]"
          >
            {FILE_SUFFIX_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <span className="text-[11px] text-[#a0a7b2]">
            保存为 {fileName.trim() ? fileName.trim() + "." + suffix : "文件名." + suffix}
          </span>
        </div>
      ) : null}

      <div className="flex min-h-0 flex-1 flex-col">
        <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#edf0f3] bg-[#fafbfc] px-4">
          <span className="text-[11px] font-medium tracking-wide text-[#7c8797]">
            {language || "TEXT"}
          </span>
          <span className="text-[10px] text-[#a0a7b2]">
            UTF-8 · {lineCount} 行
          </span>
        </div>

        {error ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 px-5 text-center">
            <FileCode2 size={28} strokeWidth={1.4} className="text-[#c4cbd4]" />
            <div className="text-sm text-[#394150]">文件内容加载失败</div>
            <div className="max-w-[560px] break-all text-xs text-[#8b94a2]">{error}</div>
            <Button size="sm" variant="secondary" onClick={onRetry}>
              重试
            </Button>
          </div>
        ) : loading ? (
          <div className="flex flex-1 items-center justify-center gap-2 text-xs text-[#8b94a2]">
            <LoaderCircle size={15} className="animate-spin" />
            正在读取文件内容
          </div>
        ) : (
          <div className="flex min-h-0 flex-1 overflow-hidden bg-white">
            <div className="w-12 shrink-0 select-none overflow-hidden border-r border-[#f0f2f5] bg-[#fafbfc] pt-3 text-right font-mono text-[12px] leading-6 text-[#b4bbc5]">
              <div style={{ transform: "translateY(-" + scrollTop + "px)" }}>
                {Array.from({ length: lineCount }, (_, index) => (
                  <div key={index} className="h-6 px-3">
                    {index + 1}
                  </div>
                ))}
              </div>
            </div>
            <textarea
              aria-label={preview ? "文件内容预览" : "文件内容编辑器"}
              value={content}
              readOnly={preview || saving}
              spellCheck={false}
              autoCapitalize="off"
              autoCorrect="off"
              onChange={(event) => onContentChange(event.target.value)}
              onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
              className={[
                "min-h-0 min-w-0 flex-1 resize-none overflow-auto bg-white px-4 pb-5 pt-3 font-mono text-[12px] leading-6 text-[#303846] outline-none",
                "whitespace-pre",
                preview ? "cursor-text" : "focus:bg-[#fffefe]",
              ].join(" ")}
            />
          </div>
        )}
      </div>

      <footer className="flex h-8 shrink-0 items-center justify-between border-t border-[#edf0f3] bg-[#fafbfc] px-4 text-[10px] text-[#9aa2ad]">
        <span>{preview ? "只读预览" : creating ? "编辑新文件" : "在线编辑"}</span>
        <span>{content.length.toLocaleString("zh-CN")} 个字符</span>
      </footer>
    </section>
  );
}
