import {
  Button,
  Checkbox,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  Textarea,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import {
  createAlertGroup,
  parseAlertInstanceIds,
  updateAlertGroup,
  type AlertChannelRecord,
  type AlertGroupRecord,
} from "@/service/alert";

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

interface Props {
  open: boolean;
  record?: AlertGroupRecord;
  channels: AlertChannelRecord[];
  onClose: () => void;
  onSaved: () => void;
}

export function AlertGroupForm({ open, record, channels, onClose, onSaved }: Props) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [errors, setErrors] = useState<{ name?: string; channels?: string }>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(record?.groupName ?? "");
    setDescription(record?.description ?? "");
    setSelectedIds(parseAlertInstanceIds(record?.alertInstanceIds));
    setErrors({});
  }, [open, record]);

  const toggle = (id: number, checked: boolean) => {
    setSelectedIds((current) =>
      checked ? Array.from(new Set([...current, id])) : current.filter((item) => item !== id),
    );
    setErrors((current) => ({ ...current, channels: "" }));
  };

  const handleSubmit = async () => {
    const nextErrors: { name?: string; channels?: string } = {};
    if (!name.trim()) nextErrors.name = "请输入通知组名称";
    if (selectedIds.length === 0) nextErrors.channels = "请至少选择一个通知渠道";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0 || saving) return;

    setSaving(true);
    try {
      if (record) {
        await updateAlertGroup(record.id, name.trim(), selectedIds, description.trim());
      } else {
        await createAlertGroup(name.trim(), selectedIds, description.trim());
      }
      onClose();
      onSaved();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      centered
      width={620}
      bodyClassName="py-3"
      title={record ? "编辑通知组" : "新建通知组"}
      onClose={() => {
        if (!saving) onClose();
      }}
      footer={
        <>
          <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={saving}
            onClick={() => void handleSubmit()}
          >
            {record ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.name)}>
          <FieldLabel required htmlFor="alert-group-name" className="pt-1.5">
            名称
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="alert-group-name"
              size="small"
              variant="outlined"
              maxLength={128}
              value={name}
              placeholder="请输入通知组名称"
              onChange={(event) => {
                setName(event.target.value);
                setErrors((current) => ({ ...current, name: "" }));
              }}
            />
            <FieldError match={Boolean(errors.name)} className="mt-1">
              {errors.name}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.channels)}>
          <FieldLabel required className="pt-1.5">
            通知渠道
          </FieldLabel>
          <div className="min-w-0">
            <div className="max-h-[220px] overflow-y-auto rounded-md border border-[var(--yak-components-control-border)] px-3 py-2">
              {channels.length > 0 ? (
                <div className="space-y-1">
                  {channels.map((channel) => {
                    const checked = selectedIds.includes(channel.id);
                    return (
                      <label
                        key={channel.id}
                        className="flex cursor-pointer items-center justify-between gap-3 rounded px-1 py-1.5 text-sm hover:bg-[var(--yak-color-hover)]"
                      >
                        <span className="flex min-w-0 items-center gap-2">
                          <Checkbox
                            checked={checked}
                            aria-label={`选择通知渠道 ${channel.instanceName}`}
                            onCheckedChange={(next) => toggle(channel.id, next === true)}
                          />
                          <span className="truncate">{channel.instanceName}</span>
                        </span>
                        <span className="shrink-0 text-xs text-[var(--yak-components-muted-text)]">
                          {channel.alertPluginName || "-"}
                        </span>
                      </label>
                    );
                  })}
                </div>
              ) : (
                <div className="py-5 text-center text-sm text-[var(--yak-components-muted-text)]">
                  暂无通知渠道
                </div>
              )}
            </div>
            <FieldError match={Boolean(errors.channels)} className="mt-1">
              {errors.channels}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS}>
          <FieldLabel htmlFor="alert-group-description" className="pt-1.5">
            描述
          </FieldLabel>
          <Textarea
            id="alert-group-description"
            size="small"
            variant="outlined"
            rows={2}
            maxLength={500}
            value={description}
            className="min-h-[56px] resize-none"
            placeholder="请输入描述"
            onValueChange={(value) => setDescription(String(value || ""))}
          />
        </Field>
      </div>
    </Modal>
  );
}
