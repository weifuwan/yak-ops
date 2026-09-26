import { Button, Field, FieldError, FieldLabel, Input, Modal, Textarea } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import { createWorkspace } from "@/service/workspace";

type WorkspaceFormProps = {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
};

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export default function WorkspaceForm({ open, onClose, onSaved }: WorkspaceFormProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [nameError, setNameError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName("");
    setDescription("");
    setNameError("");
  }, [open]);

  const handleSubmit = async () => {
    const normalizedName = name.trim();
    if (!normalizedName) {
      setNameError("请输入工作空间名称");
      return;
    }
    if (normalizedName.length > 128) {
      setNameError("工作空间名称不能超过 128 个字符");
      return;
    }
    if (saving) return;

    setSaving(true);
    try {
      await createWorkspace({
        name: normalizedName,
        description: description.trim() || undefined,
      });
      onClose();
      onSaved();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      width={600}
      bodyClassName="py-3"
      title="新建工作空间"
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
            创建
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
