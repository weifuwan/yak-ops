import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  PasswordInput,
} from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";

import {
  createUser,
  listQueues,
  listTenants,
  updateUser,
  type QueueOption,
  type TenantOption,
  type UserRecord,
  type UserSavePayload,
} from "@/service/user";

type UserFormProps = {
  open: boolean;
  record?: UserRecord;
  onClose: () => void;
  onSaved: () => void;
};

type FormValues = {
  userName: string;
  password: string;
  tenantId: string;
  queue: string;
  email: string;
  phone: string;
  state: string;
  timeZone: string;
};

type FormErrors = Partial<Record<keyof FormValues, string>>;

const EMPTY_VALUES: FormValues = {
  userName: "",
  password: "",
  tenantId: "",
  queue: "",
  email: "",
  phone: "",
  state: "1",
  timeZone: "",
};

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";
const CONTROL_CLASS =
  "h-8 w-full rounded-md border border-[var(--yak-components-input-border)] bg-white px-2.5 text-sm text-[var(--yak-components-panel-text)] outline-none focus:border-[var(--yak-color-primary)]";

export default function UserForm({ open, record, onClose, onSaved }: UserFormProps) {
  const editing = Boolean(record?.id);
  const [values, setValues] = useState<FormValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);
  const tenantsQuery = useQuery({ queryKey: ["management", "user-form", "tenants"], queryFn: listTenants, enabled: open });
  const queuesQuery = useQuery({ queryKey: ["management", "user-form", "queues"], queryFn: listQueues, enabled: open });
  const tenants = tenantsQuery.data ?? [];
  const queues = queuesQuery.data ?? [];
  const selectedTenant = tenants.find((tenant) => String(tenant.id) === values.tenantId);

  const tenantQueues = useMemo(() => {
    if (!selectedTenant) return queues;
    const matched = queues.find((queue) => queue.id === selectedTenant.queueId);
    return matched ? [matched] : queues;
  }, [queues, selectedTenant]);

  useEffect(() => {
    if (!open) return;
    setValues({
      userName: record?.userName ?? "",
      password: "",
      tenantId: record ? String(record.tenantId) : "",
      queue: record?.queue ?? "",
      email: record?.email ?? "",
      phone: record?.phone ?? "",
      state: String(record?.state ?? 1),
      timeZone: record?.timeZone ?? "",
    });
    setErrors({});
  }, [open, record]);

  useEffect(() => {
    if (!open || editing || values.tenantId || tenants.length === 0) return;
    setValues((current) => ({ ...current, tenantId: String(tenants[0].id) }));
  }, [open, editing, values.tenantId, tenants]);

  useEffect(() => {
    if (!open || !selectedTenant || values.queue) return;
    const tenantQueue = queues.find((queue) => queue.id === selectedTenant.queueId);
    if (tenantQueue) setValues((current) => ({ ...current, queue: tenantQueue.queue }));
    else if (selectedTenant.queue) setValues((current) => ({ ...current, queue: selectedTenant.queue ?? "" }));
  }, [open, selectedTenant, queues, values.queue]);

  const patch = (key: keyof FormValues, value: string) => {
    setValues((current) => ({
      ...current,
      [key]: value,
      ...(key === "tenantId" ? { queue: "" } : {}),
    }));
    setErrors((current) => ({ ...current, [key]: undefined, ...(key === "tenantId" ? { queue: undefined } : {}) }));
  };

  const validate = () => {
    const next: FormErrors = {};
    if (!values.userName.trim()) next.userName = "请输入用户名";
    if (!editing && !values.password) next.password = "请输入初始密码";
    if (values.password && (values.password.length < 6 || values.password.length > 20)) {
      next.password = "密码长度需为 6～20 位";
    }
    if (!values.tenantId) next.tenantId = "请选择租户";
    if (!values.email.trim()) next.email = "请输入邮箱";
    if (!values.phone.trim()) next.phone = "请输入手机号";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async () => {
    if (saving || !validate()) return;
    setSaving(true);
    try {
      const payload: UserSavePayload = {
        userName: values.userName.trim(),
        userPassword: values.password,
        tenantId: Number(values.tenantId),
        queue: values.queue,
        email: values.email.trim(),
        phone: values.phone.trim(),
        state: Number(values.state),
        timeZone: values.timeZone.trim() || undefined,
      };
      if (editing && record) await updateUser(record.id, payload);
      else await createUser(payload);
      onClose();
      onSaved();
    } catch {
      // The shared HTTP layer already reports request and business errors.
    } finally {
      setSaving(false);
    }
  };

  const renderSelect = (
    id: string,
    value: string,
    options: Array<{ value: string; label: string }>,
    placeholder: string,
    onChange: (value: string) => void,
    disabled = false,
  ) => (
    <select id={id} className={CONTROL_CLASS} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)}>
      <option value="">{placeholder}</option>
      {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>
  );

  const renderField = (
    key: keyof FormValues,
    label: string,
    control: React.ReactNode,
    required = false,
  ) => (
    <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors[key])}>
      <FieldLabel required={required} className="pt-1.5 text-xs leading-4">{label}</FieldLabel>
      <div className="min-w-0">
        {control}
        <FieldError match={Boolean(errors[key])} className="mt-1">{errors[key]}</FieldError>
      </div>
    </Field>
  );

  return (
    <Modal
      open={open}
      centered
      width={600}
      bodyClassName="py-3"
      title={editing ? "编辑用户" : "新增用户"}
      onClose={() => { if (!saving) onClose(); }}
      footer={
        <>
          <Button variant="secondary" size="sm" isDisabled={saving} onClick={onClose}>取消</Button>
          <Button size="sm" variant="primary" isPending={saving} onClick={() => void handleSubmit()}>
            {editing ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        {renderField("userName", "用户名",
          <Input size="small" variant="outlined" value={values.userName} readOnly={editing} placeholder="请输入用户名" onChange={(event) => patch("userName", event.target.value)} />, true)}
        {renderField("password", editing ? "新密码" : "初始密码",
          <PasswordInput size="small" variant="outlined" value={values.password} placeholder={editing ? "留空表示不修改密码" : "请输入初始密码"} onChange={(event) => patch("password", event.target.value)} />, !editing)}
        {renderField("tenantId", "所属租户",
          renderSelect("management-user-tenant", values.tenantId, tenants.map((tenant: TenantOption) => ({ value: String(tenant.id), label: tenant.tenantCode })), tenantsQuery.isLoading ? "正在加载租户..." : "请选择租户", (value) => patch("tenantId", value), tenantsQuery.isLoading), true)}
        {renderField("queue", "队列",
          renderSelect("management-user-queue", values.queue, tenantQueues.map((queue: QueueOption) => ({ value: queue.queue, label: queue.queueName + " (" + queue.queue + ")" })), queuesQuery.isLoading ? "正在加载队列..." : "请选择队列", (value) => patch("queue", value), queuesQuery.isLoading))}
        {renderField("email", "邮箱",
          <Input size="small" variant="outlined" value={values.email} placeholder="请输入邮箱" onChange={(event) => patch("email", event.target.value)} />, true)}
        {renderField("phone", "手机号",
          <Input size="small" variant="outlined" value={values.phone} placeholder="请输入手机号" onChange={(event) => patch("phone", event.target.value)} />, true)}
        {renderField("state", "用户状态",
          renderSelect("management-user-state", values.state, [{ value: "1", label: "启用" }, { value: "0", label: "禁用" }], "请选择状态", (value) => patch("state", value)), true)}
        {renderField("timeZone", "时区",
          <Input size="small" variant="outlined" value={values.timeZone} placeholder="例如 Asia/Shanghai" onChange={(event) => patch("timeZone", event.target.value)} />)}
      </div>
    </Modal>
  );
}
