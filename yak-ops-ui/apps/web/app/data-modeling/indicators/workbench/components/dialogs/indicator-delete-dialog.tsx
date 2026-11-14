import { Button, Modal } from "@yak-ops/yak-ui";
import type { IndicatorDeleteTarget } from "../../types";
import { getDefinition } from "../../config/indicator-definitions";
import { recordName } from "../../utils/indicator-form";

type Props = { target: IndicatorDeleteTarget | null; onClose: () => void; onDelete: () => void };

export function IndicatorDeleteDialog({ target, onClose, onDelete }: Props) {
  return <Modal open={Boolean(target)} centered width={420} title={"删除" + (target ? getDefinition(target.kind).title : "指标")} onClose={onClose} footer={<>
    <Button size="sm" variant="secondary" onClick={onClose}>取消</Button>
    <Button size="sm" variant="danger" onClick={onDelete}>删除</Button>
  </>}>
    <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">确定删除“{target ? recordName(target.record) : ""}”吗？如果该指标已被其他建模对象引用，删除可能会失败。</p>
  </Modal>;
}
