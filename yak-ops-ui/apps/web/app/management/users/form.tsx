import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  PasswordInput,
} from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";

import { createUser, updateUser, type UserRecord, type UserSavePayload } from "@/service/user";

type UserFormProps = {
  open: boolean;
  record?: UserRecord;
  onClose: () => void;
  onSaved: () => void;
};

type FormValues = {
  userName: string;
  password: string;
  realName: string;
  phone: string;
  email: string;
};

type FormErrors = Partial<Record<keyof FormValues, string>>;

const EMPTY_VALUES: FormValues = {
  userName: "",
  password: "",
  realName: "",
  phone: "",
  email: "",
};

const USERNAME_PATTERN = /^[0-9a-zA-Z_]{3,50}$/;
const PHONE_PATTERN = /^1\d{10}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

export default function UserForm({ open, record, onClose, onSaved }: UserFormProps) {
  const editing = Boolean(record?.id);
  const [values, setValues] = useState<FormValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setValues({
      userName: record?.userName || "",
      password: "",
      realName: record?.realName || "",
      phone: "",
      email: record?.email || "",
    });
    setErrors({});
  }, [open, record]);

  const patch = (key: keyof FormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    const userName = values.userName.trim();
    const phone = values.phone.trim();
    const email = values.email.trim();

    if (!userName) next.userName = "请输入用户名";
    else if (!USERNAME_PATTERN.test(userName))
      next.userName = "用户名需为 3～50 位字母、数字或下划线";

    if (!editing && !values.password) next.password = "请输入初始密码";
    else if (values.password && (values.password.length < 8 || values.password.length > 64)) {
      next.password = "密码长度需为 8～64 位";
    }

    if (phone && !PHONE_PATTERN.test(phone)) next.phone = "请输入 11 位手机号码";
    if (email && !EMAIL_PATTERN.test(email)) next.email = "请输入正确的邮箱地址";

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async () => {
    if (saving || !validate()) return;
    setSaving(true);
    try {
      const payload: UserSavePayload = {
        userName: values.userName.trim(),
        pw: values.password || undefined,
        realName: values.realName.trim() || undefined,
        phone: values.phone.trim() || undefined,
        email: values.email.trim() || undefined,
      };

      if (editing) await updateUser(payload);
      else await createUser(payload);

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
      width={600}
      bodyClassName="py-3"
      title={editing ? "编辑用户" : "新增用户"}
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
            {editing ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.userName)}>
          <FieldLabel required htmlFor="management-user-name" className="pt-1.5 text-xs leading-4">
            用户名
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="management-user-name"
              size="small"
              variant="outlined"
              value={values.userName}
              readOnly={editing}
              aria-invalid={Boolean(errors.userName) || undefined}
              placeholder="请输入用户名"
              onChange={(event) => patch("userName", event.target.value)}
            />
            <FieldError match={Boolean(errors.userName)} className="mt-1">
              {errors.userName}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.password)}>
          <FieldLabel
            required={!editing}
            htmlFor="management-user-password"
            className="pt-1.5 text-xs leading-4"
          >
            {editing ? "新密码" : "初始密码"}
          </FieldLabel>
          <div className="min-w-0">
            <PasswordInput
              id="management-user-password"
              size="small"
              variant="outlined"
              value={values.password}
              aria-invalid={Boolean(errors.password) || undefined}
              placeholder={editing ? "留空表示不修改密码" : "请输入 8～64 位密码"}
              onChange={(event) => patch("password", event.target.value)}
            />
            <FieldError match={Boolean(errors.password)} className="mt-1">
              {errors.password}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS}>
          <FieldLabel htmlFor="management-user-real-name" className="pt-1.5 text-xs leading-4">
            姓名
          </FieldLabel>
          <Input
            id="management-user-real-name"
            size="small"
            variant="outlined"
            value={values.realName}
            placeholder="请输入姓名"
            onChange={(event) => patch("realName", event.target.value)}
          />
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.phone)}>
          <FieldLabel htmlFor="management-user-phone" className="pt-1.5 text-xs leading-4">
            手机号
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="management-user-phone"
              size="small"
              variant="outlined"
              value={values.phone}
              aria-invalid={Boolean(errors.phone) || undefined}
              placeholder={
                editing && record?.phone ? `当前：${record.phone}，留空表示不修改` : "请输入手机号"
              }
              onChange={(event) => patch("phone", event.target.value)}
            />
            <FieldError match={Boolean(errors.phone)} className="mt-1">
              {errors.phone}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.email)}>
          <FieldLabel htmlFor="management-user-email" className="pt-1.5 text-xs leading-4">
            邮箱
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="management-user-email"
              size="small"
              variant="outlined"
              value={values.email}
              aria-invalid={Boolean(errors.email) || undefined}
              placeholder="请输入邮箱"
              onChange={(event) => patch("email", event.target.value)}
            />
            <FieldError match={Boolean(errors.email)} className="mt-1">
              {errors.email}
            </FieldError>
          </div>
        </Field>
      </div>
    </Modal>
  );
}
