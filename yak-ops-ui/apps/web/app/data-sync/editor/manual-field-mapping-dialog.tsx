import { useState } from "react";
import { Alert, Button, Modal, Textarea } from "@yak-ops/yak-ui";
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DirectFieldMapping } from "@/service/data-sync/transforms";

interface Props {
  fields: DirectFieldMapping[];
  sourceColumns: DataSourceCatalogColumn[];
  targetColumns?: DataSourceCatalogColumn[];
  onApply: (fields: DirectFieldMapping[]) => void;
  onClose: () => void;
}

const parseLines = (text: string) => text.split(/\r?\n/).map((name) => name.trim()).filter(Boolean);

export function ManualFieldMappingDialog({ fields, sourceColumns, targetColumns, onApply, onClose }: Props) {
  const [sourceText, setSourceText] = useState(() => fields.map((field) => field.source).join("\n"));
  const [targetText, setTargetText] = useState(() => fields.map((field) => field.target).join("\n"));
  const [error, setError] = useState("");

  const apply = () => {
    const source = parseLines(sourceText);
    const target = parseLines(targetText);
    if (!source.length || source.length !== target.length) {
      setError("请至少填写一组映射，并确保两侧字段数量一致");
      return;
    }
    const sourceByName = new Map(sourceColumns.map((column) => [column.name.toLocaleLowerCase(), column.name]));
    const targetByName = targetColumns
      ? new Map(targetColumns.map((column) => [column.name.toLocaleLowerCase(), column.name]))
      : undefined;
    const mappings: DirectFieldMapping[] = [];
    for (let index = 0; index < source.length; index++) {
      const sourceName = sourceByName.get(source[index].toLocaleLowerCase());
      const targetName = targetByName ? targetByName.get(target[index].toLocaleLowerCase()) : target[index];
      if (!sourceName || !targetName) {
        setError("字段不存在：" + (!sourceName ? source[index] : target[index]));
        return;
      }
      mappings.push({ source: sourceName, target: targetName });
    }
    if (
      new Set(mappings.map((field) => field.source.toLocaleLowerCase())).size !== mappings.length ||
      new Set(mappings.map((field) => field.target.toLocaleLowerCase())).size !== mappings.length
    ) {
      setError("来源字段和目标字段均不能重复映射");
      return;
    }
    onApply(mappings);
    onClose();
  };

  return (
    <Modal
      open
      title="手动编辑字段映射"
      width={800}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>确认</Button>
          <Button size="small" onClick={onClose}>取消</Button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          每行填写一个字段，两侧按照行号对应；空行会被忽略。
        </p>
        <div className="grid grid-cols-2 gap-3">
          <label className="space-y-2 text-xs">
            <span>来源字段</span>
            <Textarea
              aria-label="手动来源字段"
              size="small"
              variant="outlined"
              className="min-h-56 font-mono"
              value={sourceText}
              onValueChange={(value) => setSourceText(String(value))}
            />
          </label>
          <label className="space-y-2 text-xs">
            <span>目标字段</span>
            <Textarea
              aria-label="手动目标字段"
              size="small"
              variant="outlined"
              className="min-h-56 font-mono"
              value={targetText}
              onValueChange={(value) => setTargetText(String(value))}
            />
          </label>
        </div>
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          来源字段：{parseLines(sourceText).length} · 目标字段：{parseLines(targetText).length}
        </p>
        {error ? <Alert variant="error">{error}</Alert> : null}
      </div>
    </Modal>
  );
}
