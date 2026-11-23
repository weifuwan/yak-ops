import { useMemo, useState } from "react";
import { Button, Modal, Textarea } from "@yak-ops/yak-ui";

import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import { parseImportedFields } from "./field-grid-utils";
import type { FieldKind } from "./field-grid-types";

export function FieldBatchImportDialog({
  open,
  fieldType,
  onClose,
  onImport,
}: {
  open: boolean;
  fieldType: FieldKind;
  onClose: () => void;
  onImport: (fields: DimensionFieldRecord[]) => void;
}) {
  const [text, setText] = useState("");
  const parsed = useMemo(() => parseImportedFields(text, fieldType), [fieldType, text]);

  const close = () => {
    setText("");
    onClose();
  };

  const submit = () => {
    if (!parsed.length) return;
    onImport(parsed);
    close();
  };

  return (
    <Modal
      open={open}
      centered
      width={700}
      title="批量导入字段"
      onClose={close}
      footer={
        <>
          <Button size="sm" variant="secondary" onClick={close}>
            取消
          </Button>
          <Button size="sm" variant="primary" isDisabled={!parsed.length} onClick={submit}>
            导入 {parsed.length || ""} 行
          </Button>
        </>
      }
    >
      <div className="space-y-2">
        <Textarea
          variant="outlined"
          rows={12}
          value={text}
          placeholder={"可直接从 Excel 粘贴。列顺序：字段名称、类型、字段显示名、描述、长度、精度、主键、非空、来源表、来源字段"}
          onValueChange={setText}
        />
        <div className="text-xs text-[var(--yak-components-muted-text)]">
          支持 Tab 分隔或逗号分隔；首行是“字段名称”时会自动识别为表头。
        </div>
      </div>
    </Modal>
  );
}

export default FieldBatchImportDialog;
