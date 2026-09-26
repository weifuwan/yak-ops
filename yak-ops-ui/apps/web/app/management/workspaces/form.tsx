import { Button, Field, FieldError, FieldLabel, Input, Modal, Textarea } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import { createWorkspace } from "@/service/workspace";

type WorkspaceFormProps = {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
};

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
      width={520}
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
      <div className="space-y-4">
        <Field invalid={Boolean(nameError)}>
          <FieldLabel required htmlFor="management-workspace-name">
            工作空间名称
          </FieldLabel>
          <Input
            id="management-workspace-name"
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
          <FieldError match={Boolean(nameError)}>{nameError}</FieldError>
        </Field>

        <Field>
          <FieldLabel htmlFor="management-workspace-description">描述</FieldLabel>
          <Textarea
            id="management-workspace-description"
            rows={3}
            maxLength={500}
            value={description}
            placeholder="请输入工作空间描述"
            onValueChange={(value) => setDescription(String(value || ""))}
          />
        </Field>
      </div>
    </Modal>
  );
}
