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
import { FilePlus2, FolderOpen } from "lucide-react";

const FILE_SUFFIX_OPTIONS = [
  { value: "sql", label: "SQL" },
  { value: "py", label: "Python" },
  { value: "sh", label: "Shell" },
  { value: "bat", label: "Batch" },
  { value: "cfg", label: "CFG" },
  { value: "hql", label: "HQL" },
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
  { value: "ini", label: "INI" },
];

interface ResourceCreateFileModalProps {
  open: boolean;
  currentDirectory: string;
  fileName: string;
  suffix: string;
  content: string;
  saving: boolean;
  onFileNameChange: (value: string) => void;
  onSuffixChange: (value: string) => void;
  onContentChange: (value: string) => void;
  onSubmit: () => void;
  onClose: () => void;
}

export function ResourceCreateFileModal({
  open,
  currentDirectory,
  fileName,
  suffix,
  content,
  saving,
  onFileNameChange,
  onSuffixChange,
  onContentChange,
  onSubmit,
  onClose,
}: ResourceCreateFileModalProps) {
  const normalizedName = fileName.trim();
  const fileNameHasSuffix = normalizedName.toLowerCase().endsWith("." + suffix);
  const previewName = normalizedName
    ? fileNameHasSuffix
      ? normalizedName
      : normalizedName + "." + suffix
    : "文件名称." + suffix;

  return (
    <Modal
      open={open}
      centered
      width={680}
      title={
        <span className="flex items-center gap-2">
          <span className="flex size-7 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
            <FilePlus2 size={15} />
          </span>
          新建文件
        </span>
      }
      bodyClassName="space-y-4 py-4"
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
            isDisabled={!normalizedName || !content.length}
            onClick={onSubmit}
          >
            创建文件
          </Button>
        </>
      }
    >
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
          <FieldLabel
            required
            htmlFor="resource-create-file-name"
            className="text-xs"
          >
            文件名称
          </FieldLabel>
          <Input
            id="resource-create-file-name"
            size="small"
            variant="outlined"
            autoFocus
            maxLength={128}
            value={fileName}
            disabled={saving}
            placeholder="例如：daily_sync"
            onChange={(event) => onFileNameChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onSubmit();
              }
            }}
          />
        </Field>

        <Field className="min-w-0 !gap-1.5">
          <FieldLabel
            required
            htmlFor="resource-create-file-suffix"
            className="text-xs"
          >
            文件类型
          </FieldLabel>
          <Select
            size="small"
            items={FILE_SUFFIX_OPTIONS}
            value={suffix}
            onValueChange={(value) => onSuffixChange(String(value ?? "sql"))}
          >
            <SelectTrigger
              id="resource-create-file-suffix"
              variant="outlined"
              className="w-full"
            >
              <SelectValue placeholder="选择类型" />
            </SelectTrigger>
            <SelectContent>
              {FILE_SUFFIX_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <SelectItemText>{option.label}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      </div>

      <Field className="!gap-1.5">
        <div className="flex items-center justify-between gap-3">
          <FieldLabel
            required
            htmlFor="resource-create-file-content"
            className="text-xs"
          >
            文件内容
          </FieldLabel>
          <span className="text-[11px] tabular-nums text-[var(--yak-components-muted-text)]">
            {content.length.toLocaleString("zh-CN")} 字符
          </span>
        </div>
        <Textarea
          id="resource-create-file-content"
          size="small"
          variant="outlined"
          rows={10}
          value={content}
          disabled={saving}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          placeholder="在这里输入文件内容..."
          className="min-h-[220px] resize-y font-mono text-[12px] leading-6"
          onValueChange={(value) => onContentChange(String(value ?? ""))}
        />
      </Field>

      <div className="flex items-center justify-between gap-3 text-[11px] text-[var(--yak-components-muted-text)]">
        <span>文件将以 UTF-8 编码保存</span>
        <span className="truncate" title={previewName}>
          创建为 <span className="font-medium text-[var(--yak-components-panel-text)]">{previewName}</span>
        </span>
      </div>
    </Modal>
  );
}
