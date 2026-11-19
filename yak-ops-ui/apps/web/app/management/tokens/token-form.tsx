import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
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
} from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import { listAllUsers, type UserBriefRecord } from "@/service/user";
import {
  createAccessToken,
  updateAccessToken,
  type AccessTokenRecord,
  type AccessTokenSavePayload,
} from "@/service/access-token";

interface Props {
  open: boolean;
  record?: AccessTokenRecord;
  onClose: () => void;
  onSaved: (record: AccessTokenRecord) => void;
}

interface FormValues {
  userId: string;
  expireTime: string;
}

type FormErrors = Partial<Record<keyof FormValues, string>>;

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

const toDateTimeInput = (value?: string | null) =>
  value ? value.replace(" ", "T").slice(0, 16) : "";

const toApiDateTime = (value: string) => value.replace("T", " ") + ":00";

const defaultExpireTime = () => {
  const date = new Date();
  date.setFullYear(date.getFullYear() + 1);
  const pad = (value: number) => String(value).padStart(2, "0");
  return (
    String(date.getFullYear()) +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
};

export function AccessTokenForm({ open, record, onClose, onSaved }: Props) {
  const { currentUser } = useAuth();
  const isAdmin = currentUser?.userType === "ADMIN_USER";
  const currentUserId = currentUser?.id ?? "";
  const [values, setValues] = useState<FormValues>({
    userId: currentUserId,
    expireTime: defaultExpireTime(),
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);

  const usersQuery = useQuery({
    queryKey: ["management", "access-tokens", "users"],
    queryFn: listAllUsers,
    enabled: open && isAdmin,
  });
  const users = usersQuery.data ?? [];

  useEffect(() => {
    if (!open) return;
    setValues({
      userId: record?.userId ?? currentUserId,
      expireTime: toDateTimeInput(record?.expireTime) || defaultExpireTime(),
    });
    setErrors({});
  }, [open, record, currentUserId]);

  const patch = (key: keyof FormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    if (!values.userId || !Number.isInteger(Number(values.userId)) || Number(values.userId) <= 0) {
      next.userId = "请选择所属用户";
    }
    if (!values.expireTime) {
      next.expireTime = "请选择有效期";
    } else if (new Date(values.expireTime).getTime() <= Date.now()) {
      next.expireTime = "有效期必须晚于当前时间";
    }
    if (isAdmin && usersQuery.isError) {
      next.userId = "用户列表加载失败，请关闭后重试";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async () => {
    if (saving || !validate()) return;
    setSaving(true);
    try {
      const payload: AccessTokenSavePayload = {
        userId: values.userId,
        expireTime: toApiDateTime(values.expireTime),
      };
      const saved = record
        ? await updateAccessToken(record.id, payload)
        : await createAccessToken(payload);
      onClose();
      onSaved(saved);
    } catch {
      // The shared HTTP layer already reports request and business errors.
    } finally {
      setSaving(false);
    }
  };

  const userOptions = users.map((user: UserBriefRecord) => ({
    value: user.id,
    label: user.userName,
  }));
  if (record && !userOptions.some((option) => option.value === record.userId)) {
    userOptions.unshift({
      value: record.userId,
      label: record.userName || "用户 ID：" + record.userId,
    });
  }

  return (
    <Modal
      open={open}
      centered
      width={600}
      bodyClassName="py-3"
      title={record ? "编辑 Token" : "新增 Token"}
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
            isDisabled={isAdmin && usersQuery.isPending}
            onClick={() => void handleSubmit()}
          >
            {record ? "保存并重新生成" : "创建 Token"}
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.userId)}>
          <FieldLabel required className="pt-1.5 text-xs leading-4">
            所属用户
          </FieldLabel>
          <div className="min-w-0">
            {isAdmin ? (
              <Select<string>
                size="small"
                disabled={usersQuery.isPending || usersQuery.isError}
                items={Object.fromEntries(userOptions.map((option) => [option.value, option.label]))}
                value={values.userId || null}
                onValueChange={(value) => patch("userId", value ?? "")}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue
                    placeholder={
                      usersQuery.isPending
                        ? "正在加载用户..."
                        : usersQuery.isError
                          ? "用户列表加载失败"
                          : "请选择用户"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {userOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <SelectItemText>{option.label}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <Input size="small" variant="outlined" value={currentUser?.userName ?? ""} readOnly />
            )}
            <FieldError match={Boolean(errors.userId)} className="mt-1">
              {errors.userId}
            </FieldError>
          </div>
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.expireTime)}>
          <FieldLabel required className="pt-1.5 text-xs leading-4">
            有效期至
          </FieldLabel>
          <div className="min-w-0">
            <Input
              size="small"
              variant="outlined"
              type="datetime-local"
              value={values.expireTime}
              onChange={(event) => patch("expireTime", event.target.value)}
            />
            <FieldError match={Boolean(errors.expireTime)} className="mt-1">
              {errors.expireTime}
            </FieldError>
          </div>
        </Field>

        {record ? (
          <div className="rounded-md bg-[var(--yak-color-hover)] px-3 py-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            保存后会生成新的 Token，原 Token 将立即失效。请及时更新使用该 Token 的调用方配置。
          </div>
        ) : (
          <div className="rounded-md bg-[var(--yak-color-hover)] px-3 py-2 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            Token 将由服务端自动生成。创建成功后请复制并妥善保存。
          </div>
        )}
      </div>
    </Modal>
  );
}
