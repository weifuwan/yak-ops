import { Button, Modal } from "@yak-ops/yak-ui";

import type { BusinessProcessRecord } from "../types";

interface Props {
  record?: BusinessProcessRecord; deleting: boolean;
  onClose: () => void; onConfirm: () => void;
}

export function BusinessProcessDeleteDialog({ record, deleting, onClose, onConfirm }: Props) {
  return (
    <Modal open={Boolean(record)} centered width={420} title="删除业务过程" onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={deleting} onClick={onClose}>取消</Button>
        <Button size="sm" variant="danger" isPending={deleting} onClick={onConfirm}>删除</Button>
      </>}>
      <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
        确定删除“{record?.cnName || "当前业务过程"}”吗？删除后不可恢复。
      </p>
    </Modal>
  );
}

export default BusinessProcessDeleteDialog;
