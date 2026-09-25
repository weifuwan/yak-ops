import {
  Button,
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerTitle,
  Input,
  PasswordInput,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { X } from "lucide-react";
import { useEffect, useState } from "react";

import {
  createDataSource,
  testDataSourceConnectionWithParams,
  updateDataSource,
} from "@/service/datasource";
import { COMMON_DB_OPTIONS, JDBC_URL_PLACEHOLDERS } from "./constants";
import DatabaseIcons from "./icons/DatabaseIcons";
import { useIntl } from "./i18n";
import type { DataSourceRecord, DataSourceSavePayload } from "./types";

interface DataSourceFormProps {
  open: boolean;
  record?: DataSourceRecord;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  dbType: string;
  jdbcUrl: string;
  username: string;
  password: string;
  remark: string;
}

type FormErrors = Partial<Record<keyof FormValues, string>>;

const EMPTY_FORM: FormValues = {
  name: "",
  dbType: "MYSQL",
  jdbcUrl: "",
  username: "",
  password: "",
  remark: "",
};

const parseOriginalJson = (record?: DataSourceRecord): Partial<FormValues> => {
  if (!record?.originalJson) return {};
  try {
    const value = JSON.parse(record.originalJson);
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    return {
      jdbcUrl: typeof value.jdbcUrl === "string" ? value.jdbcUrl : undefined,
      username: typeof value.username === "string" ? value.username : undefined,
      password: typeof value.password === "string" ? value.password : undefined,
    };
  } catch {
    return {};
  }
};

const DataSourceForm = ({ open, record, onOpenChange, onSaved }: DataSourceFormProps) => {
  const intl = useIntl();
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [testing, setTesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const editing = Boolean(record?.id);
  const busy = testing || submitting;

  useEffect(() => {
    if (!open) return;
    const original = parseOriginalJson(record);
    setValues({
      ...EMPTY_FORM,
      name: record?.name || "",
      dbType: record?.dbType || "MYSQL",
      jdbcUrl: original.jdbcUrl || record?.jdbcUrl || "",
      username: original.username || "",
      password: original.password || "",
      remark: record?.remark || "",
    });
    setErrors({});
  }, [open, record]);

  const patch = <K extends keyof FormValues>(key: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    if (!values.name.trim()) next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameRequired" });
    if (!values.dbType) next.dbType = intl.formatMessage({ id: "pages.datasource.form.dbTypeRequired" });
    if (!values.jdbcUrl.trim()) next.jdbcUrl = intl.formatMessage({ id: "pages.datasource.form.jdbcUrlRequired" });
    if (!values.username.trim()) next.username = intl.formatMessage({ id: "pages.datasource.form.usernameRequired" });
    if (values.name.length > 128) next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameMax" });
    if (values.remark.length > 500) next.remark = intl.formatMessage({ id: "pages.datasource.form.descriptionMax" });
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const connectionJson = () =>
    JSON.stringify({
      dbType: values.dbType,
      jdbcUrl: values.jdbcUrl.trim(),
      username: values.username.trim(),
      password: values.password,
    });

  const handleTest = async () => {
    if (busy || !validate()) return;
    setTesting(true);
    try {
      const connected = await testDataSourceConnectionWithParams({
        dataSourceId: record?.id,
        dbType: values.dbType,
        connJson: connectionJson(),
      });
      if (connected) toast.success(intl.formatMessage({ id: "pages.datasource.test.success" }));
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    if (busy || !validate()) return;
    setSubmitting(true);
    try {
      const payload: DataSourceSavePayload = {
        name: values.name.trim(),
        dbType: values.dbType,
        environment: record?.environment || "DEVELOP",
        remark: values.remark.trim() || undefined,
        connectionParams: connectionJson(),
      };

      if (record?.id) await updateDataSource(record.id, payload);
      else await createDataSource(payload);

      toast.success(
        intl.formatMessage({
          id: record?.id
            ? "pages.datasource.modal.message.updateSuccess"
            : "pages.datasource.modal.message.createSuccess",
        }),
      );
      onOpenChange(false);
      onSaved();
    } finally {
      setSubmitting(false);
    }
  };

  const fieldError = (key: keyof FormValues) =>
    errors[key] ? <div className="mt-1 text-xs text-[#b42318]">{errors[key]}</div> : null;

  return (
    <Drawer
      open={open}
      side="right"
      disablePointerDismissal={busy}
      onOpenChange={(next) => {
        if (!busy) onOpenChange(next);
      }}
    >
      <DrawerContent width={520} className="bg-white">
        <div className="flex items-center gap-3 border-b border-[#eef0f3] px-5 py-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-[#eaecf0] bg-[#f7f8fa]">
            <DatabaseIcons dbType={values.dbType} width="18" height="18" />
          </div>
          <div className="min-w-0 flex-1">
            <DrawerTitle className="text-[15px] font-semibold">
              {intl.formatMessage({
                id: editing
                  ? "pages.datasource.modal.drawerTitle.edit"
                  : "pages.datasource.modal.drawerTitle.add",
              })}
            </DrawerTitle>
            <DrawerDescription className="sr-only">
              {intl.formatMessage({ id: "pages.datasource.common.title" })}
            </DrawerDescription>
          </div>
          <Button
            variant="ghost"
            size="small"
            disabled={busy}
            className="h-8 w-8 p-0"
            onClick={() => onOpenChange(false)}
          >
            <X size={16} />
          </Button>
        </div>

        <DrawerBody className="px-5 py-5">
          <div className="space-y-4">
            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.dsName" })}
              </span>
              <Input
                value={values.name}
                aria-invalid={Boolean(errors.name) || undefined}
                placeholder={intl.formatMessage({ id: "pages.datasource.form.dsNamePlaceholder" })}
                onChange={(event) => patch("name", event.target.value)}
              />
              {fieldError("name")}
            </label>

            <div>
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.dbType" })}
              </span>
              <Select
                value={values.dbType}
                disabled={editing}
                onValueChange={(value) => patch("dbType", value ?? "")}
              >
                <SelectTrigger aria-invalid={Boolean(errors.dbType) || undefined}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {COMMON_DB_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      <SelectItemText>{option.label}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {fieldError("dbType")}
            </div>

            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.jdbcUrl" })}
              </span>
              <Input
                value={values.jdbcUrl}
                aria-invalid={Boolean(errors.jdbcUrl) || undefined}
                placeholder={JDBC_URL_PLACEHOLDERS[values.dbType] || intl.formatMessage({ id: "pages.datasource.form.jdbcUrlPlaceholder" })}
                onChange={(event) => patch("jdbcUrl", event.target.value)}
              />
              {fieldError("jdbcUrl")}
            </label>

            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.username" })}
              </span>
              <Input
                value={values.username}
                aria-invalid={Boolean(errors.username) || undefined}
                placeholder={intl.formatMessage({ id: "pages.datasource.form.usernamePlaceholder" })}
                onChange={(event) => patch("username", event.target.value)}
              />
              {fieldError("username")}
            </label>

            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.password" })}
              </span>
              <PasswordInput
                value={values.password}
                placeholder={intl.formatMessage({ id: "pages.datasource.form.passwordPlaceholder" })}
                onChange={(event) => patch("password", event.target.value)}
              />
            </label>

            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
                {intl.formatMessage({ id: "pages.datasource.form.description" })}
              </span>
              <Textarea
                rows={3}
                maxLength={500}
                value={values.remark}
                aria-invalid={Boolean(errors.remark) || undefined}
                placeholder={intl.formatMessage({ id: "pages.datasource.form.descriptionPlaceholder" })}
                onValueChange={(value) => patch("remark", value)}
              />
              {fieldError("remark")}
            </label>
          </div>
        </DrawerBody>

        <div className="flex items-center justify-between border-t border-[#eef0f3] px-5 py-3">
          <Button disabled={busy} onClick={() => onOpenChange(false)}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
          <div className="flex gap-2">
            <Button loading={testing} disabled={submitting} onClick={() => void handleTest()}>
              {intl.formatMessage({ id: "pages.datasource.modal.button.connTest" })}
            </Button>
            <Button
              variant="primary"
              loading={submitting}
              disabled={testing}
              onClick={() => void handleSubmit()}
            >
              {intl.formatMessage({
                id: editing
                  ? "pages.datasource.modal.button.save"
                  : "pages.datasource.modal.button.create",
              })}
            </Button>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
};

export default DataSourceForm;
