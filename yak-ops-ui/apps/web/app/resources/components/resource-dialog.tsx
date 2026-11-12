import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
} from "@yak-ops/yak-ui";

import type { ResourceDialog as ResourceDialogState } from "../types";
import { getName, isDirectoryItem } from "../utils";

interface ResourceDialogProps {
  dialog: ResourceDialogState | null;
  value: string;
  pending: boolean;
  onValueChange: (value: string) => void;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}

export function ResourceDialog({
  dialog,
  value,
  pending,
  onValueChange,
  onConfirm,
  onClose,
}: ResourceDialogProps) {
  return (
    <Dialog
      open={Boolean(dialog)}
      onOpenChange={(open) => {
        if (!open && !pending) onClose();
      }}
    >
      <DialogContent className="w-[420px]">
        <DialogTitle className="text-base font-semibold">
          {dialog?.kind === "create"
            ? "新建文件夹"
            : dialog?.kind === "rename"
              ? "重命名资源"
              : dialog?.kind === "delete-selected"
                ? "批量删除资源"
                : "删除资源"}
        </DialogTitle>

        {dialog?.kind === "delete" ? (
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            {isDirectoryItem(dialog.item)
              ? "确定删除文件夹“" + getName(dialog.item) + "”及其全部内容吗？此操作无法撤销。"
              : "确定删除文件“" + getName(dialog.item) + "”吗？此操作无法撤销。"}
          </DialogDescription>
        ) : dialog?.kind === "delete-selected" ? (
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除选中的 {dialog.items.length} 项资源吗？删除文件夹时，其下的内容也会一并删除。
          </DialogDescription>
        ) : (
          <div className="mt-4">
            <Input
              size="small"
              aria-label="资源名称"
              placeholder={dialog?.kind === "create" ? "请输入文件夹名称" : "请输入新名称"}
              value={value}
              onChange={(event) => onValueChange(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  void onConfirm();
                }
              }}
            />
          </div>
        )}

        <div className="mt-6 flex justify-end gap-2">
          <Button
            size="sm"
            variant="secondary"
            isDisabled={pending}
            onClick={onClose}
          >
            取消
          </Button>
          <Button
            size="sm"
            variant={dialog?.kind === "delete" || dialog?.kind === "delete-selected" ? "danger" : undefined}
            isPending={pending}
            onClick={() => void onConfirm()}
          >
            {dialog?.kind === "delete" || dialog?.kind === "delete-selected" ? "删除" : "确定"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
