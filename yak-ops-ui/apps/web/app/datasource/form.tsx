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
import { COMMON_DB_OPTIONS, JDBC_URL_PLACEHOLDERS, type DataSourceCategory } from "./constants";
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
type CreateCategory = "ALL" | DataSourceCategory;

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
  const [createCategory, setCreateCategory] = useState<CreateCategory>("ALL");
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
      setCreateCategory("ALL");
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
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-name" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.dsName" })}
      </label>
      <div className="min-w-0">
        <Input
          id="datasource-name"
          size="small"
          variant="outlined"
          value={values.name}
          aria-invalid={Boolean(errors.name) || undefined}
          placeholder={intl.formatMessage({ id: "pages.datasource.form.dsNamePlaceholder" })}
          onChange={(event) => patch("name", event.target.value)}
        />
        {fieldError("name")}
      </div>
    </div>
  );

  const dbTypeField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-db-type" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.dbType" })}
      </label>
      <div className="min-w-0">
        <Select
          size="small"
          value={values.dbType}
          disabled={editing}
          onValueChange={(value) => patch("dbType", value ?? "")}
        >
          <SelectTrigger
            id="datasource-db-type"
            variant="outlined"
            aria-invalid={Boolean(errors.dbType) || undefined}
          >
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
    </div>
  );

  const jdbcUrlField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-jdbc-url" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.jdbcUrl" })}
      </label>
      <div className="min-w-0">
        <Input
          id="datasource-jdbc-url"
          size="small"
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
      </div>
    </div>
  );

  const usernameField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-username" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.username" })}
      </label>
      <div className="min-w-0">
        <Input
          id="datasource-username"
          size="small"
          variant="outlined"
          value={values.username}
          aria-invalid={Boolean(errors.username) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.usernamePlaceholder",
          })}
          onChange={(event) => patch("username", event.target.value)}
        />
        {fieldError("username")}
      </div>
    </div>
  );

  const passwordField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-password" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.password" })}
      </label>
      <PasswordInput
        id="datasource-password"
        size="small"
        variant="outlined"
        value={values.password}
        placeholder={intl.formatMessage({
          id: "pages.datasource.form.passwordPlaceholder",
        })}
        onChange={(event) => patch("password", event.target.value)}
      />
    </div>
  );

  const remarkField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <label htmlFor="datasource-remark" className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.description" })}
      </label>
      <div className="min-w-0">
        <Textarea
          id="datasource-remark"
          size="small"
          rows={2}
          maxLength={500}
          value={values.remark}
          className="min-h-[56px] resize-none"
          aria-invalid={Boolean(errors.remark) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.descriptionPlaceholder",
          })}
          onValueChange={(value) => patch("remark", value)}
        />
        {fieldError("remark")}
      </div>
    </div>
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

          <DrawerBody className="px-5 py-4">
            <div className="space-y-2.5">
              {nameField}
              {dbTypeField}
              {jdbcUrlField}
              {usernameField}
              {passwordField}
              {remarkField}
            </div>
          </DrawerBody>

          <div className="flex items-center justify-between border-t border-[#eef0f3] px-5 py-3">
            <Button size="small" disabled={busy} onClick={() => onOpenChange(false)}>
              {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
            </Button>
            <div className="flex gap-2">
              <Button
                size="small"
                loading={testing}
                disabled={submitting}
                onClick={() => void handleTest()}
              >
                {intl.formatMessage({ id: "pages.datasource.modal.button.connTest" })}
              </Button>
              <Button
                size="small"
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
  const relationalCount = COMMON_DB_OPTIONS.filter(
    (option) => option.category === "RELATIONAL",
  ).length;
  const visibleOptions = COMMON_DB_OPTIONS.filter((option) => {
    const matchesCategory = createCategory === "ALL" || option.category === createCategory;
    const matchesSearch =
      !normalizedSearch ||
      option.label.toLowerCase().includes(normalizedSearch) ||
      option.value.toLowerCase().includes(normalizedSearch);
    return matchesCategory && matchesSearch;
  });

  return (
    <Modal
      open={open}
      width={960}
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      title={
        createStep === "select"
          ? intl.formatMessage({ id: "pages.datasource.wizard.title" })
          : intl.formatMessage(
              { id: "pages.datasource.wizard.configTitle" },
              { type: selectedType?.label || values.dbType },
            )
      }
      bodyClassName={createStep === "config" ? "py-3" : undefined}
      footer={
        createStep === "select" ? (
          <Button size="small" disabled={busy} onClick={() => onOpenChange(false)}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
        ) : (
          <div className="flex w-full items-center justify-between">
            <Button size="small" disabled={busy} onClick={() => setCreateStep("select")}>
              {intl.formatMessage({ id: "pages.datasource.wizard.back" })}
            </Button>
            <div className="flex gap-2">
              <Button
                size="small"
                loading={testing}
                disabled={submitting}
                onClick={() => void handleTest()}
              >
                {intl.formatMessage({ id: "pages.datasource.modal.button.connTest" })}
              </Button>
              <Button
                size="small"
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
        <div className="flex h-[420px] flex-col">
          <section className="shrink-0">
            <div className="mb-2.5 text-[13px] font-medium text-[#344054]">
              {intl.formatMessage({ id: "pages.datasource.wizard.category" })}
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className={
                  createCategory === "ALL"
                    ? "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-color-primary)] bg-[var(--yak-color-primary)] px-3 text-xs text-white outline-none"
                    : "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[#d9dde3] bg-white px-3 text-xs text-[#667085] outline-none hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
                }
                onClick={() => setCreateCategory("ALL")}
              >
                {intl.formatMessage(
                  { id: "pages.datasource.wizard.categoryAll" },
                  { count: COMMON_DB_OPTIONS.length },
                )}
              </button>
              <button
                type="button"
                className={
                  createCategory === "RELATIONAL"
                    ? "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-color-primary)] bg-[var(--yak-color-primary)] px-3 text-xs text-white outline-none"
                    : "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[#d9dde3] bg-white px-3 text-xs text-[#667085] outline-none hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
                }
                onClick={() => setCreateCategory("RELATIONAL")}
              >
                {intl.formatMessage(
                  { id: "pages.datasource.wizard.categoryRelational" },
                  { count: relationalCount },
                )}
              </button>
            </div>
          </section>

          <div className="relative mt-4 shrink-0">
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

          <section className="mt-5 min-h-0 flex-1">
            <div className="mb-2.5 text-[13px] font-medium text-[#344054]">
              {intl.formatMessage({ id: "pages.datasource.wizard.datasourceList" })}
            </div>
            {visibleOptions.length > 0 ? (
              <div className="grid grid-cols-3 gap-2 max-sm:grid-cols-1">
                {visibleOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className="flex h-10 cursor-pointer items-center gap-2.5 rounded-[var(--yak-radius-control-small)] border border-[#e7e9ed] bg-white px-3 text-left text-[13px] text-[#343841] outline-none transition-[border-color,background-color] hover:border-[#cfd4dc] hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
                    onClick={() => handleSelectType(option.value)}
                  >
                    <DatabaseIcons dbType={option.value} width="18" height="18" />
                    <span className="min-w-0 truncate">{option.label}</span>
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
        <div className="space-y-3.5">
          <section className="overflow-hidden rounded-[var(--yak-radius-control-small)] border border-[#e7e9ed]">
            <h3 className="border-b border-[#eef0f3] bg-[#fafafa] px-3 py-2 text-xs font-medium text-[#344054]">
              {intl.formatMessage({ id: "pages.datasource.wizard.basicInfo" })}
            </h3>
            <div className="space-y-2.5 px-3 py-3">
              {nameField}
              {remarkField}
            </div>
          </section>

          <section>
            <h3 className="mb-2 text-xs font-medium text-[#344054]">
              {intl.formatMessage({ id: "pages.datasource.wizard.connectionConfig" })}
            </h3>
            <div className="space-y-2.5 rounded-[var(--yak-radius-control-small)] border border-[#e7e9ed] px-3 py-3">
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
