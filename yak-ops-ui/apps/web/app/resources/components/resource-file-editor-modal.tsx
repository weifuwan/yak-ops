import {
  Button,
  Field,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";
import {
  Check,
  FileCode2,
  FilePlus2,
  FolderOpen,
  LoaderCircle,
  Save,
} from "lucide-react";

import type { ResourceItemRecord } from "@/service/resources";

import { RESOURCE_FILE_TYPE_OPTIONS } from "../file-types";
import { getExtension, getName } from "../utils";
import { FileTypeIcon } from "./file-type-icon";

interface ResourceFileEditorModalProps {
  mode: "create" | "edit";
  open: boolean;
  currentDirectory: string;
  item?: ResourceItemRecord | null;
  fileName: string;
  suffix: string;
  content: string;
  loading?: boolean;
  saving: boolean;
  dirty?: boolean;
  error?: string;
  onFileNameChange?: (value: string) => void;
  onSuffixChange?: (value: string) => void;
  onContentChange: (value: string) => void;
  onSubmit: () => void;
  onClose: () => void;
  onRetry?: () => void;
}

export function ResourceFileEditorModal({
  mode,
  open,
  currentDirectory,
  item,
  fileName,
  suffix,
  content,
  loading = false,
  saving,
  dirty = false,
  error = "",
  onFileNameChange,
  onSuffixChange,
  onContentChange,
  onSubmit,
  onClose,
  onRetry,
}: ResourceFileEditorModalProps) {
  const creating = mode === "create";
  const normalizedName = fileName.trim();
  const fileNameHasSuffix = normalizedName.toLowerCase().endsWith("." + suffix);
  const previewName = normalizedName
    ? fileNameHasSuffix
      ? normalizedName
      : normalizedName + "." + suffix
    : "文件名称." + suffix;
  const itemName = item ? getName(item) : "资源文件";
  const extension = getExtension(itemName).toUpperCase() || "TEXT";
  const lineCount = Math.max(1, content.split("\n").length);
  const canSubmit = creating
    ? Boolean(normalizedName && content.length)
    : Boolean(item && dirty && content.length && !loading && !error);

  return (
    <Modal
      open={open}
      centered
      width={creating ? 680 : 880}
      className="w-full"
      title={
        <span className="flex min-w-0 items-center gap-2">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
            {creating ? (
              <FilePlus2 size={15} />
            ) : (
              <FileTypeIcon extension={getExtension(itemName)} size={20} />
            )}
          </span>
          <span>{creating ? "新建文件" : "编辑文件"}</span>
          {!creating && item ? (
            <span className="min-w-0 truncate text-xs font-normal text-[var(--yak-components-muted-text)]">
              {itemName}
            </span>
          ) : null}
        </span>
      }
      bodyClassName={creating ? "space-y-4 py-4" : "space-y-3 py-3"}
      onClose={() => {
        if (!saving) onClose();
      }}
      footer={
        <>
          <Button
            size="sm"
            variant="secondary"
            isDisabled={saving}
            onClick={onClose}
          >
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={saving}
            isDisabled={saving || !canSubmit}
            onClick={onSubmit}
          >
            {creating ? <FilePlus2 size={13} /> : <Save size={13} />}
            {creating ? "创建文件" : "保存修改"}
          </Button>
        </>
      }
    >
      {creating ? (
        <>
          <div className="flex min-w-0 items-start gap-2.5 rounded-lg border border-[#edf0f3] bg-[#fafbfc] px-3 py-2.5">
            <FolderOpen
              size={15}
              className="mt-0.5 shrink-0 text-[var(--yak-components-muted-text)]"
            />
            <div className="min-w-0 flex-1">
              <div className="mb-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
                保存位置
              </div>
              <div
                className="break-all text-xs leading-5 text-[var(--yak-components-panel-text)]"
                title={currentDirectory}
              >
                {currentDirectory || "资源根目录"}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-[minmax(0,1fr)_156px] items-start gap-3">
            <Field className="min-w-0 !gap-1.5">
              <FieldLabel required htmlFor="resource-file-name" className="text-xs">
                文件名称
              </FieldLabel>
              <Input
                id="resource-file-name"
                size="small"
                variant="outlined"
                autoFocus
                maxLength={128}
                value={fileName}
                disabled={saving}
                placeholder="例如：daily_sync"
                onChange={(event) => onFileNameChange?.(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    onSubmit();
                  }
                }}
              />
            </Field>

            <Field className="min-w-0 !gap-1.5">
              <FieldLabel required htmlFor="resource-file-suffix" className="text-xs">
                文件类型
              </FieldLabel>
              <Select
                size="small"
                items={RESOURCE_FILE_TYPE_OPTIONS}
                value={suffix}
                disabled={saving}
                onValueChange={(value) => onSuffixChange?.(String(value ?? "sql"))}
              >
                <SelectTrigger
                  id="resource-file-suffix"
                  variant="outlined"
                  className="w-full"
                >
                  <span className="flex min-w-0 items-center gap-2">
                    <FileTypeIcon extension={suffix} size={18} />
                    <SelectValue placeholder="选择类型" />
                  </span>
                </SelectTrigger>
                <SelectContent>
                  {RESOURCE_FILE_TYPE_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <span className="flex items-center gap-2">
                        <FileTypeIcon extension={option.value} size={18} />
                        <SelectItemText>{option.label}</SelectItemText>
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>
        </>
      ) : (
        <>
          <div className="flex min-w-0 items-center justify-between gap-3 rounded-lg border border-[#edf0f3] bg-[#fafbfc] px-3 py-2">
            <div className="flex min-w-0 items-center gap-2">
              <FileTypeIcon extension={getExtension(itemName)} size={20} />
              <div className="min-w-0">
                <div className="truncate text-xs font-medium text-[#394150]" title={item?.fullName}>
                  {item?.fullName}
                </div>
                <div className="mt-0.5 text-[10px] text-[#9aa2ad]">
                  UTF-8 · {extension}
                </div>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-1.5 text-[11px]">
              {dirty ? (
                <>
                  <span className="size-1.5 rounded-full bg-[#d99a45]" />
                  <span className="text-[#b7792c]">未保存</span>
                </>
              ) : (
                <>
                  <Check size={12} className="text-[#7c9b88]" />
                  <span className="text-[#8b9a90]">已保存</span>
                </>
              )}
            </div>
          </div>
        </>
      )}

      <Field className="!gap-1.5">
        <div className="flex items-center justify-between gap-3">
          <FieldLabel required htmlFor="resource-file-content" className="text-xs">
            文件内容
          </FieldLabel>
          <span className="text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">
            {creating
              ? content.length.toLocaleString("zh-CN") + " 字符"
              : lineCount.toLocaleString("zh-CN") +
                " 行 · " +
                content.length.toLocaleString("zh-CN") +
                " 字符"}
          </span>
        </div>

        {error ? (
          <div className="flex min-h-[280px] flex-col items-center justify-center gap-3 rounded-md border border-[#edf0f3] bg-[#fafbfc] px-4 text-center">
            <FileCode2 size={24} className="text-[#aab3bf]" />
            <div className="text-xs text-[#687386]">文件内容加载失败</div>
            <div className="max-w-full break-all text-[11px] leading-5 text-[#9aa2ad]">
              {error}
            </div>
            {onRetry ? (
              <Button size="sm" variant="secondary" onClick={onRetry}>
                重试
              </Button>
            ) : null}
          </div>
        ) : loading ? (
          <div className="flex min-h-[280px] items-center justify-center gap-2 rounded-md border border-[#edf0f3] bg-[#fafbfc] text-xs text-[#8b94a2]">
            <LoaderCircle size={15} className="animate-spin" />
            正在读取文件内容
          </div>
        ) : (
          <Textarea
            id="resource-file-content"
            size="small"
            variant="outlined"
            rows={creating ? 10 : 18}
            required
            value={content}
            disabled={saving}
            spellCheck={false}
            autoCapitalize="off"
            autoCorrect="off"
            placeholder={creating ? "在这里输入文件内容..." : "文件内容为空"}
            className={[
              "w-full resize-y font-mono text-[12px] leading-6",
              creating ? "min-h-[220px]" : "min-h-[360px]",
            ].join(" ")}
            onValueChange={(value) => onContentChange(String(value ?? ""))}
          />
        )}
      </Field>

      {creating ? (
        <div className="flex items-center justify-between gap-3 text-[11px] text-[var(--yak-components-muted-text)]">
          <span>文件将以 UTF-8 编码保存</span>
          <span className="truncate" title={previewName}>
            创建为{" "}
            <span className="font-medium text-[var(--yak-components-panel-text)]">
              {previewName}
            </span>
          </span>
        </div>
      ) : null}
    </Modal>
  );
}
