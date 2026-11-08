import { useState } from "react";
import { Button, Field, FieldError, FieldLabel, Input, Textarea } from "@yak-ops/yak-ui";
import { EditorDrawer } from "./editor-drawer";

export interface TaskProperties {
  name: string;
  remark: string;
}

export function PropertiesDrawer({
  id, value, syncPlan, open, onApply, onClose, onClosed, onDirtyChange,
}: {
  id?: string;
  value: TaskProperties;
  syncPlan: string;
  open: boolean;
  onApply: (value: TaskProperties) => void;
  onClose: () => void;
  onClosed: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [form, setForm] = useState(value);
  const [error, setError] = useState("");
  const change = (next: TaskProperties) => {
    setForm(next);
    setError("");
    onDirtyChange(next.name !== value.name || next.remark !== value.remark);
  };
  const apply = () => {
    if (!form.name.trim()) {
      setError("请输入任务名称");
      return;
    }
    onApply({ name: form.name.trim(), remark: form.remark.trim() });
  };
  return (
    <EditorDrawer title="属性" width={480} open={open} onClose={onClose} onClosed={onClosed}
      footer={
        <>
          <Button size="sm" variant="primary" onClick={apply}>确认</Button>
          <Button variant="secondary" size="sm" onClick={onClose}>取消</Button>
        </>
      }>
      <div className="space-y-5">
        <Field invalid={Boolean(error)} className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5">任务名称</FieldLabel>
          <div>
            <Input aria-label="任务名称" autoFocus size="small" variant="outlined"
              maxLength={128} value={form.name}
              onChange={(event) => change({ ...form, name: event.target.value })} />
            {error ? <FieldError match>{error}</FieldError> : null}
          </div>
        </Field>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] gap-3 text-sm">
          <span>同步方案</span><span>{syncPlan}</span>
        </div>
        <div className="grid grid-cols-[104px_minmax(0,1fr)] gap-3 text-sm">
          <span>任务 ID</span><span className="break-all">{id || "保存后生成"}</span>
        </div>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5">备注</FieldLabel>
          <Textarea aria-label="备注" variant="outlined" size="small" rows={4}
            maxLength={500} value={form.remark}
            onValueChange={(remark) => change({ ...form, remark })} />
        </Field>
      </div>
    </EditorDrawer>
  );
}
