import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { listQueues } from "@/service/user";
import {
  createTenant,
  updateTenant,
  verifyTenantCode,
  type TenantRecord,
  type TenantSavePayload,
} from "@/service/tenant";

type TenantFormProps = {
  open: boolean;
  record?: TenantRecord;
  onClose: () => void;
  onSaved: () => void;
};

type FormValues = {
  tenantCode: string;
  queueId: string;
  description: string;
};

type FormErrors = Partial<Record<keyof FormValues, string>>;

const EMPTY_VALUES: FormValues = {
  tenantCode: "",
  queueId: "",
  description: "",
};

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export function TenantForm({ open, record, onClose, onSaved }: TenantFormProps) {
  const editing = Boolean(record?.id);
  const [values, setValues] = useState<FormValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);
  const queuesQuery = useQuery({
    queryKey: ["management", "tenants", "queues"],
    queryFn: listQueues,
    enabled: open,
  });
  const queues = queuesQuery.data ?? [];

  useEffect(() => {
    if (!open) return;
    setValues({
      tenantCode: record?.tenantCode ?? "",
      queueId: record ? String(record.queueId) : "",
      description: record?.description ?? "",
    });
    setErrors({});
  }, [open, record]);

  const patch = (key: keyof FormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    const tenantCode = values.tenantCode.trim();
    if (!tenantCode) next.tenantCode = "请输入租户编码";
    else if (tenantCode.length > 30) next.tenantCode = "租户编码不能超过 30 个字符";
    if (!values.queueId) next.queueId = "请选择所属队列";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async () => {
    if (saving || !validate()) return;
    setSaving(true);
    try {
      const tenantCode = values.tenantCode.trim();
      if (!editing || tenantCode !== record?.tenantCode) {
        await verifyTenantCode(tenantCode);
      }
      const payload: TenantSavePayload = {
        tenantCode,
        queueId: Number(values.queueId),
        description: values.description.trim() || undefined,
      };
      if (record) await updateTenant(record.id, payload);
      else await createTenant(payload);
      onClose();
      onSaved();
    } catch {
      // The shared HTTP layer already reports request and business errors.
    } finally {
      setSaving(false);
    }
  };

  const queueOptions = queues.map((queue) => ({
    value: String(queue.id),
    label: queue.queueName + " (" + queue.queue + ")",
  }));

  return (
    <Modal
      open={open}
      centered
      width={600}
      bodyClassName="py-3"
      title={editing ? "编辑租户" : "新增租户"}
      onClose={() => {
        if (!saving) onClose();
      }}
      footer={
        <>
          <Button variant="secondary" size="sm" isDisabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={saving}
            onClick={() => void handleSubmit()}
          >
            {editing ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.tenantCode)}>
          <FieldLabel
            required
            htmlFor="management-tenant-code"
            className="pt-1.5 text-xs leading-4"
          >
            租户编码
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="management-tenant-code"
              size="small"
              variant="outlined"
              maxLength={30}
              value={values.tenantCode}
              aria-invalid={Boolean(errors.tenantCode) || undefined}
              placeholder="请输入租户编码"
              onChange={(event) => patch("tenantCode", event.target.value)}
            />
            <FieldError match={Boolean(errors.tenantCode)} className="mt-1">
              {errors.tenantCode}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.queueId)}>
          <FieldLabel
            required
            htmlFor="management-tenant-queue"
            className="pt-1.5 text-xs leading-4"
          >
            所属队列
          </FieldLabel>
          <div className="min-w-0">
            <Select<string>
              size="small"
              disabled={queuesQuery.isPending || queuesQuery.isError || queues.length === 0}
              items={Object.fromEntries(queueOptions.map((option) => [option.value, option.label]))}
              value={values.queueId || null}
              onValueChange={(value) => patch("queueId", value ?? "")}
            >
              <SelectTrigger id="management-tenant-queue" variant="outlined">
                <SelectValue
                  placeholder={
                    queuesQuery.isPending
                      ? "正在加载队列..."
                      : queuesQuery.isError
                        ? "队列加载失败"
                        : "请选择所属队列"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {queueOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldError match={Boolean(errors.queueId)} className="mt-1">
              {errors.queueId}
            </FieldError>
            {queuesQuery.isError ? (
              <p className="mt-1 text-xs text-[var(--yak-color-danger)]">队列加载失败，请稍后重试</p>
            ) : null}
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS}>
          <FieldLabel
            htmlFor="management-tenant-description"
            className="pt-1.5 text-xs leading-4"
          >
            描述
          </FieldLabel>
          <Textarea
            id="management-tenant-description"
            size="small"
            variant="outlined"
            rows={2}
            value={values.description}
            className="min-h-[56px] resize-none"
            placeholder="请输入描述"
            onValueChange={(value) => patch("description", String(value || ""))}
          />
        </Field>
      </div>
    </Modal>
  );
}
