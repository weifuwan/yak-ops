import { Button, Field, FieldError, FieldLabel, Input, Modal, Textarea } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import { useWorkspace } from "@/hooks/use-workspace";
import { updateWorkspace, type WorkspaceRecord } from "@/service/workspace";

type WorkspaceFormProps = {
  open: boolean;
  record?: WorkspaceRecord;
  onClose: () => void;
  onSaved: () => void;
};

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export default function WorkspaceForm({ open, record, onClose, onSaved }: WorkspaceFormProps) {
  const { createWorkspace } = useWorkspace();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [nameError, setNameError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(record?.name ?? "");
    setDescription(record?.description ?? "");
    setNameError("");
  }, [open, record]);

  const handleSubmit = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError("请输入工作空间名称");
      return;
    }
    if (trimmedName.length > 128) {
      setNameError("工作空间名称不能超过 128 个字符");
      return;
    }
    if (saving) return;

    setSaving(true);
    try {
      const payload = { name: trimmedName, description: description.trim() || undefined };
      if (record) await updateWorkspace(record.id, payload);
      else await createWorkspace(payload);
      onClose();
      onSaved();
    } catch {
      // The HTTP layer displays the request failure; keep the form open.
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      centered
      width={600}
      bodyClassName="py-3"
      title={record ? "编辑工作空间" : "新建工作空间"}
      onClose={() => {
        if (!saving) onClose();
      }}
      footer={
        <>
          <Button size="small" disabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            loading={saving}
            onClick={() => void handleSubmit()}
          >
            {record ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(nameError)}>
          <FieldLabel
            required
            htmlFor="management-workspace-name"
            className="pt-1.5 text-xs leading-4"
          >
            工作空间名称
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="management-workspace-name"
              size="small"
              variant="outlined"
              maxLength={128}
              value={name}
              aria-invalid={Boolean(nameError) || undefined}
              placeholder="请输入工作空间名称"
              onChange={(event) => {
                setName(event.target.value);
                setNameError("");
              }}
            />
            <FieldError match={Boolean(nameError)} className="mt-1">
              {nameError}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS}>
          <FieldLabel
            htmlFor="management-workspace-description"
            className="pt-1.5 text-xs leading-4"
          >
            描述
          </FieldLabel>
          <Textarea
            id="management-workspace-description"
            size="small"
            variant="outlined"
            rows={2}
            maxLength={500}
            value={description}
            className="min-h-[56px] resize-none"
            placeholder="请输入工作空间描述"
            onValueChange={(value) => setDescription(String(value || ""))}
          />
        </Field>
      </div>
    </Modal>
  );
}
