import { Button, Dialog, DialogContent, DialogDescription, DialogTitle } from "@yak-ops/yak-ui";
import type { PendingTabClose } from "../../types";

type Props = { pendingClose: PendingTabClose | null; onCancel: () => void; onConfirm: () => void };

export function IndicatorTabCloseDialog({ pendingClose, onCancel, onConfirm }: Props) {
  return <Dialog open={Boolean(pendingClose)} onOpenChange={(open) => { if (!open) onCancel(); }}>
    {pendingClose ? <DialogContent className="w-[420px]">
      <DialogTitle className="text-base font-semibold">关闭未保存的标签页</DialogTitle>
      <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">{pendingClose.dirtyTitles.join("、")}存在未保存的修改。关闭后，这些修改将丢失，确定继续吗？</DialogDescription>
      <div className="mt-6 flex justify-end gap-2"><Button size="sm" variant="secondary" onClick={onCancel}>取消</Button><Button size="sm" variant="danger" onClick={onConfirm}>仍然关闭</Button></div>
    </DialogContent> : null}
  </Dialog>;
}
