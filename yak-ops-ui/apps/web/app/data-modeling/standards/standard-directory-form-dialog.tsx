import { useEffect, useState } from "react";
import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemText, SelectTrigger, SelectValue,
} from "@yak-ops/yak-ui";

import type { StandardDirectoryRecord, StandardModule } from "./types";

interface Props {
  open: boolean;
  module: StandardModule;
  parentId?: string;
  editing?: StandardDirectoryRecord;
  initialDirType?: string;
  saving: boolean;
  onClose: () => void;
  onSubmit: (values: Record<string, unknown>) => void;
}

const FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export function StandardDirectoryFormDialog({
  open, module, parentId, editing, initialDirType, saving, onClose, onSubmit,
}: Props) {
  const [dirName, setDirName] = useState("");
  const [dirType, setDirType] = useState("FOLDER");
  const [error, setError] = useState("");
  const options = module === "FIELD"
    ? [{ value: "FOLDER", label: "文件夹" }, { value: "STANDARD_SET", label: "标准集" }]
    : [{ value: "FOLDER", label: "文件夹" }];

  useEffect(() => {
    if (!open) return;
    setDirName(editing?.dirName || "");
    setDirType(editing?.dirType || initialDirType || "FOLDER");
    setError("");
  }, [open, editing, initialDirType]);

  const submit = () => {
    const name = dirName.trim();
    if (!name) return setError("请输入目录名称");
    if (name.length > 128) return setError("目录名称不能超过 128 个字符");
    onSubmit({
      ...(editing?.id ? { id: editing.id } : {}),
      dirName: name,
      dirType,
      dirModule: module,
      parentId: parentId ?? editing?.parentId ?? null,
    });
  };

  return (
    <Modal
      open={open}
      centered
      width={560}
      title={editing ? "编辑目录" : dirType === "STANDARD_SET" ? "新建标准集" : "新建目录"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}
    >
      <div className="space-y-3">
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">目录名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={128} value={dirName}
            placeholder="请输入目录名称" onChange={(event) => { setDirName(event.target.value); setError(""); }} />
        </Field>
        <Field className={FIELD_CLASS}>
          <FieldLabel required className="pt-1.5 text-xs">目录类型</FieldLabel>
          <Select value={dirType} disabled={Boolean(editing)} onValueChange={(value) => setDirType(String(value || "FOLDER"))}>
            <SelectTrigger size="small" variant="outlined" className="w-full">
              <SelectValue placeholder="请选择目录类型" />
            </SelectTrigger>
            <SelectContent>
              {options.map((item) => (
                <SelectItem key={item.value} value={item.value}>
                  <SelectItemText>{item.label}</SelectItemText>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        {parentId ? (
          <p className="pl-[116px] text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            新目录将自动归入当前选中的上级目录
          </p>
        ) : null}
        {error ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{error}</p> : null}
      </div>
    </Modal>
  );
}

export default StandardDirectoryFormDialog;
