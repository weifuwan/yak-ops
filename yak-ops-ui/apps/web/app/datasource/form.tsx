import {
  Button,
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerTitle,
  Input,
  Modal,
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
import { Search, X } from "lucide-react";
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
type CreateStep = "select" | "config";

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
  const [createStep, setCreateStep] = useState<CreateStep>("select");
  const [createSearch, setCreateSearch] = useState("");
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
    if (!record?.id) {
      setCreateStep("select");
      setCreateSearch("");
    }
  }, [open, record]);

  const patch = <K extends keyof FormValues>(key: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    if (!values.name.trim())
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameRequired" });
    if (!values.dbType)
      next.dbType = intl.formatMessage({ id: "pages.datasource.form.dbTypeRequired" });
    if (!values.jdbcUrl.trim())
      next.jdbcUrl = intl.formatMessage({ id: "pages.datasource.form.jdbcUrlRequired" });
    if (!values.username.trim())
      next.username = intl.formatMessage({ id: "pages.datasource.form.usernameRequired" });
    if (values.name.length > 128)
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameMax" });
    if (values.remark.length > 500)
      next.remark = intl.formatMessage({ id: "pages.datasource.form.descriptionMax" });
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

  const handleSelectType = (dbType: string) => {
    setValues((current) => ({
      ...current,
      dbType,
      jdbcUrl: current.dbType === dbType ? current.jdbcUrl : "",
      username: current.dbType === dbType ? current.username : "",
      password: current.dbType === dbType ? current.password : "",
    }));
    setErrors({});
    setCreateStep("config");
  };

  const fieldError = (key: keyof FormValues) =>
    errors[key] ? <div className="mt-1 text-xs text-[#b42318]">{errors[key]}</div> : null;

  const nameField = (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.dsName" })}
      </span>
      <Input
        variant="outlined"
        value={values.name}
        aria-invalid={Boolean(errors.name) || undefined}
        placeholder={intl.formatMessage({ id: "pages.datasource.form.dsNamePlaceholder" })}
        onChange={(event) => patch("name", event.target.value)}
      />
      {fieldError("name")}
    </label>
  );

  const dbTypeField = (
    <div>
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.dbType" })}
      </span>
      <Select
        value={values.dbType}
        disabled={editing}
        onValueChange={(value) => patch("dbType", value ?? "")}
      >
        <SelectTrigger variant="outlined" aria-invalid={Boolean(errors.dbType) || undefined}>
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
  );

  const jdbcUrlField = (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.jdbcUrl" })}
      </span>
      <Input
        variant="outlined"
        value={values.jdbcUrl}
        aria-invalid={Boolean(errors.jdbcUrl) || undefined}
        placeholder={
          JDBC_URL_PLACEHOLDERS[values.dbType] ||
          intl.formatMessage({ id: "pages.datasource.form.jdbcUrlPlaceholder" })
        }
        onChange={(event) => patch("jdbcUrl", event.target.value)}
      />
      {fieldError("jdbcUrl")}
    </label>
  );

  const usernameField = (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.username" })}
      </span>
      <Input
        variant="outlined"
        value={values.username}
        aria-invalid={Boolean(errors.username) || undefined}
        placeholder={intl.formatMessage({
          id: "pages.datasource.form.usernamePlaceholder",
        })}
        onChange={(event) => patch("username", event.target.value)}
      />
      {fieldError("username")}
    </label>
  );

  const passwordField = (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.password" })}
      </span>
      <PasswordInput
        variant="outlined"
        value={values.password}
        placeholder={intl.formatMessage({
          id: "pages.datasource.form.passwordPlaceholder",
        })}
        onChange={(event) => patch("password", event.target.value)}
      />
    </label>
  );

  const remarkField = (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.description" })}
      </span>
      <Textarea
        rows={3}
        maxLength={500}
        value={values.remark}
        aria-invalid={Boolean(errors.remark) || undefined}
        placeholder={intl.formatMessage({
          id: "pages.datasource.form.descriptionPlaceholder",
        })}
        onValueChange={(value) => patch("remark", value)}
      />
      {fieldError("remark")}
    </label>
  );

  if (editing) {
    return (
      <Drawer
        open={open}
        side="right"
        disablePointerDismissal={busy}
        onOpenChange={(next) => {
          if (!busy) onOpenChange(next);
        }}
      >
        <DrawerContent width={520} animated={false} className="bg-white">
          <div className="flex items-center gap-3 border-b border-[#eef0f3] px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-[#eaecf0] bg-[#f7f8fa]">
              <DatabaseIcons dbType={values.dbType} width="18" height="18" />
            </div>
            <div className="min-w-0 flex-1">
              <DrawerTitle className="text-[15px] font-semibold">
                {intl.formatMessage({ id: "pages.datasource.modal.drawerTitle.edit" })}
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
              {nameField}
              {dbTypeField}
              {jdbcUrlField}
              {usernameField}
              {passwordField}
              {remarkField}
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
                {intl.formatMessage({ id: "pages.datasource.modal.button.save" })}
              </Button>
            </div>
          </div>
        </DrawerContent>
      </Drawer>
    );
  }

  const selectedType = COMMON_DB_OPTIONS.find((option) => option.value === values.dbType);
  const normalizedSearch = createSearch.trim().toLowerCase();
  const visibleOptions = COMMON_DB_OPTIONS.filter(
    (option) =>
      !normalizedSearch ||
      option.label.toLowerCase().includes(normalizedSearch) ||
      option.value.toLowerCase().includes(normalizedSearch),
  );

  return (
    <Modal
      open={open}
      width={820}
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      title={
        createStep === "select"
          ? intl.formatMessage({ id: "pages.datasource.modal.drawerTitle.add" })
          : intl.formatMessage(
              { id: "pages.datasource.wizard.configTitle" },
              { type: selectedType?.label || values.dbType },
            )
      }
      footer={
        createStep === "select" ? (
          <Button disabled={busy} onClick={() => onOpenChange(false)}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
        ) : (
          <div className="flex w-full items-center justify-between">
            <Button disabled={busy} onClick={() => setCreateStep("select")}>
              {intl.formatMessage({ id: "pages.datasource.wizard.back" })}
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
                {intl.formatMessage({ id: "pages.datasource.wizard.complete" })}
              </Button>
            </div>
          </div>
        )
      }
    >
      {createStep === "select" ? (
        <div className="space-y-5">
          <div className="relative">
            <Search
              aria-hidden="true"
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#98a2b3]"
            />
            <Input
              variant="outlined"
              value={createSearch}
              className="pl-9"
              placeholder={intl.formatMessage({
                id: "pages.datasource.wizard.searchPlaceholder",
              })}
              onChange={(event) => setCreateSearch(event.target.value)}
            />
          </div>

          <section>
            <div className="mb-3 text-[13px] font-medium text-[#344054]">
              {intl.formatMessage({ id: "pages.datasource.wizard.commonTypes" })}
            </div>
            {visibleOptions.length > 0 ? (
              <div className="grid grid-cols-3 gap-3 max-sm:grid-cols-1">
                {visibleOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className="flex min-h-20 cursor-pointer items-center gap-3 rounded-[var(--yak-radius-control-medium)] border border-[#e7e9ed] bg-white px-4 text-left outline-none transition-[border-color,background-color] hover:border-[#cfd4dc] hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
                    onClick={() => handleSelectType(option.value)}
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg border border-[#eaecf0] bg-[#f7f8fa]">
                      <DatabaseIcons dbType={option.value} width="22" height="22" />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-sm font-medium text-[#252832]">{option.label}</span>
                      <span className="mt-1 block text-xs text-[#98a2b3]">
                        {intl.formatMessage({ id: "pages.datasource.wizard.jdbcDatabase" })}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            ) : (
              <div className="py-10 text-center text-sm text-[#98a2b3]">
                {intl.formatMessage({ id: "pages.datasource.wizard.empty" })}
              </div>
            )}
          </section>
        </div>
      ) : (
        <div className="space-y-7">
          <section>
            <h3 className="mb-4 text-sm font-semibold text-[#252832]">
              {intl.formatMessage({ id: "pages.datasource.wizard.basicInfo" })}
            </h3>
            <div className="space-y-4">
              {nameField}
              {remarkField}
            </div>
          </section>

          <section className="border-t border-[#eef0f3] pt-6">
            <h3 className="mb-4 text-sm font-semibold text-[#252832]">
              {intl.formatMessage({ id: "pages.datasource.wizard.connectionConfig" })}
            </h3>
            <div className="space-y-4">
              {jdbcUrlField}
              {usernameField}
              {passwordField}
            </div>
          </section>
        </div>
      )}
    </Modal>
  );
};

export default DataSourceForm;
