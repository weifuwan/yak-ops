import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  FieldRequiredMark,
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
import { Search } from "lucide-react";
import { useEffect, useState } from "react";

import {
  createDataSource,
  testDataSourceConnectionWithParams,
  updateDataSource,
} from "@/service/datasource";
import {
  COMMON_DB_OPTIONS,
  JDBC_DEFAULT_PORTS,
  normalizeDataSourceType,
  type DataSourceCategory,
} from "./constants";
import DatabaseIcons from "./icons/DatabaseIcons";
import { useIntl } from "./i18n";
import type { DataSourceConnectionParams, DataSourceRecord, DataSourceSavePayload } from "./types";

interface DataSourceFormProps {
  open: boolean;
  record?: DataSourceRecord;
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}

interface JdbcProperty {
  key: string;
  value: string;
}

interface FormValues {
  name: string;
  dbType: string;
  host: string;
  port: string;
  database: string;
  username: string;
  password: string;
  properties: JdbcProperty[];
  remark: string;
}

type FormErrorKey =
  | "name"
  | "dbType"
  | "host"
  | "port"
  | "database"
  | "username"
  | "properties"
  | "remark";
type FormErrors = Partial<Record<FormErrorKey, string>>;
type CreateStep = "select" | "config";
type CreateCategory = "ALL" | DataSourceCategory;

interface ParsedJdbcUrl {
  host?: string;
  port?: string;
  database?: string;
}

const DEFAULT_HOST = "127.0.0.1";

const defaultPort = (dbType: string) =>
  String(JDBC_DEFAULT_PORTS[normalizeDataSourceType(dbType)] || "");

const EMPTY_FORM: FormValues = {
  name: "",
  dbType: "MYSQL",
  host: DEFAULT_HOST,
  port: defaultPort("MYSQL"),
  database: "",
  username: "",
  password: "",
  properties: [],
  remark: "",
};

const parseJdbcUrl = (dbType: string, jdbcUrl?: string): ParsedJdbcUrl => {
  if (!jdbcUrl) return {};
  const value = jdbcUrl.trim();
  const normalizedType = normalizeDataSourceType(dbType);
  const patterns: Record<string, RegExp> = {
    MYSQL: /^jdbc:mysql:\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
    ORACLE: /^jdbc:oracle:thin:@\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
    POSTGRE_SQL: /^jdbc:postgresql:\/\/(\[[^\]]+\]|[^:/?#]+)(?::(\d+))?\/([^?]+)(?:\?.*)?$/i,
  };
  const pattern = patterns[normalizedType];
  if (!pattern) return {};
  const matched = value.match(pattern);
  if (!matched) return {};
  return {
    host: matched[1],
    port: matched[2] || defaultPort(normalizedType),
    database: matched[3],
  };
};

const parseProperties = (value: unknown): JdbcProperty[] => {
  if (!value || typeof value !== "object" || Array.isArray(value)) return [];
  return Object.entries(value as Record<string, unknown>).map(([key, propertyValue]) => ({
    key,
    value: propertyValue == null ? "" : String(propertyValue),
  }));
};

const parseOriginalJson = (record?: DataSourceRecord): Partial<FormValues> => {
  const dbType = normalizeDataSourceType(record?.dbType) || "MYSQL";
  if (!record?.originalJson) {
    return {
      ...parseJdbcUrl(dbType, record?.jdbcUrl),
      port: parseJdbcUrl(dbType, record?.jdbcUrl).port || defaultPort(dbType),
    };
  }
  try {
    const value = JSON.parse(record.originalJson);
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    const jdbc = parseJdbcUrl(
      dbType,
      typeof value.jdbcUrl === "string" ? value.jdbcUrl : record?.jdbcUrl,
    );
    return {
      host: typeof value.host === "string" && value.host.trim() ? value.host : jdbc.host,
      port:
        typeof value.port === "number" || typeof value.port === "string"
          ? String(value.port)
          : jdbc.port || defaultPort(dbType),
      database:
        typeof value.database === "string" && value.database.trim()
          ? value.database
          : jdbc.database,
      username: typeof value.username === "string" ? value.username : undefined,
      password: typeof value.password === "string" ? value.password : undefined,
      properties: parseProperties(value.properties),
    };
  } catch {
    return {
      ...parseJdbcUrl(dbType, record?.jdbcUrl),
      port: parseJdbcUrl(dbType, record?.jdbcUrl).port || defaultPort(dbType),
    };
  }
};

const buildJdbcPreview = (values: FormValues) => {
  const host = values.host.trim();
  const port = values.port.trim();
  const database = values.database.trim();
  const authority = host + (port ? ":" + port : "");

  switch (normalizeDataSourceType(values.dbType)) {
    case "ORACLE":
      return "jdbc:oracle:thin:@//" + authority + "/" + database;
    case "POSTGRE_SQL":
      return "jdbc:postgresql://" + authority + "/" + database;
    default:
      return "jdbc:mysql://" + authority + "/" + database;
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
    const dbType = normalizeDataSourceType(record?.dbType) || "MYSQL";
    const original = parseOriginalJson(record);
    setValues({
      ...EMPTY_FORM,
      name: record?.name || "",
      dbType,
      host: original.host || DEFAULT_HOST,
      port: original.port || defaultPort(dbType),
      database: original.database || "",
      username: original.username || "",
      password: original.password || "",
      properties: original.properties || [],
      remark: record?.remark || "",
    });
    setErrors({});
    setCreateStep(record?.id ? "config" : "select");
    if (!record?.id) {
      setCreateSearch("");
      setCreateCategory("ALL");
    }
  }, [open, record]);

  const patch = <K extends keyof FormValues>(key: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    if (key in errors) {
      setErrors((current) => ({ ...current, [key]: undefined }));
    }
  };

  const patchProperty = (index: number, key: keyof JdbcProperty, value: string) => {
    setValues((current) => ({
      ...current,
      properties: current.properties.map((property, propertyIndex) =>
        propertyIndex === index ? { ...property, [key]: value } : property,
      ),
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const addProperty = () => {
    setValues((current) => ({
      ...current,
      properties: [...current.properties, { key: "", value: "" }],
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const removeProperty = (index: number) => {
    setValues((current) => ({
      ...current,
      properties: current.properties.filter((_, propertyIndex) => propertyIndex !== index),
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    const port = Number(values.port);
    if (!values.name.trim())
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameRequired" });
    if (!values.dbType)
      next.dbType = intl.formatMessage({ id: "pages.datasource.form.dbTypeRequired" });
    if (!values.host.trim())
      next.host = intl.formatMessage({ id: "pages.datasource.form.hostRequired" });
    if (!values.port.trim())
      next.port = intl.formatMessage({ id: "pages.datasource.form.portRequired" });
    else if (!Number.isInteger(port) || port < 1 || port > 65535)
      next.port = intl.formatMessage({ id: "pages.datasource.form.portInvalid" });
    if (!values.database.trim())
      next.database = intl.formatMessage({ id: "pages.datasource.form.databaseRequired" });
    if (!values.username.trim())
      next.username = intl.formatMessage({ id: "pages.datasource.form.usernameRequired" });
    if (values.name.length > 128)
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameMax" });
    if (values.remark.length > 500)
      next.remark = intl.formatMessage({ id: "pages.datasource.form.descriptionMax" });

    const propertyKeys = values.properties.map((property) => property.key.trim());
    if (propertyKeys.some((key) => !key)) {
      next.properties = intl.formatMessage({ id: "pages.datasource.form.propertyKeyRequired" });
    } else if (new Set(propertyKeys).size !== propertyKeys.length) {
      next.properties = intl.formatMessage({ id: "pages.datasource.form.propertyKeyDuplicate" });
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const connectionParams = (): DataSourceConnectionParams => ({
    host: values.host.trim(),
    port: Number(values.port),
    database: values.database.trim(),
    username: values.username.trim(),
    password: values.password,
    properties: Object.fromEntries(
      values.properties.map((property) => [property.key.trim(), property.value]),
    ),
  });

  const handleTest = async () => {
    if (busy || !validate()) return;
    setTesting(true);
    try {
      const connected = await testDataSourceConnectionWithParams({
        dataSourceId: record?.id,
        dbType: values.dbType,
        connectionParams: connectionParams(),
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
        connectionParams: connectionParams(),
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
    const normalizedType = normalizeDataSourceType(dbType);
    setValues((current) =>
      current.dbType === normalizedType
        ? current
        : {
            ...current,
            dbType: normalizedType,
            host: DEFAULT_HOST,
            port: defaultPort(normalizedType),
            database: "",
            username: "",
            password: "",
            properties: [],
          },
    );
    setErrors({});
    setCreateStep("config");
  };

  const nameField = (
    <Field
      invalid={Boolean(errors.name)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-name" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.dsName" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-name"
          required
          size="small"
          variant="outlined"
          value={values.name}
          aria-invalid={Boolean(errors.name) || undefined}
          placeholder={intl.formatMessage({ id: "pages.datasource.form.dsNamePlaceholder" })}
          onChange={(event) => patch("name", event.target.value)}
        />
        <FieldError match={Boolean(errors.name)} className="mt-1">
          {errors.name}
        </FieldError>
      </div>
    </Field>
  );

  const jdbcPreviewField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.jdbcPreview" })}
      </span>
      <div className="min-h-7 break-all py-1.5 text-xs text-[#667085]">
        {buildJdbcPreview(values)}
      </div>
    </div>
  );

  const connectionAddressField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        <FieldRequiredMark />
        {intl.formatMessage({ id: "pages.datasource.form.connectionAddress" })}
      </span>
      <div className="grid min-w-0 grid-cols-[minmax(0,1fr)_160px] gap-2 max-sm:grid-cols-1">
        <Field invalid={Boolean(errors.host)} className="!gap-0">
          <Input
            id="datasource-host"
            required
            size="small"
            variant="outlined"
            value={values.host}
            aria-label={intl.formatMessage({ id: "pages.datasource.form.hostPlaceholder" })}
            aria-invalid={Boolean(errors.host) || undefined}
            placeholder={intl.formatMessage({ id: "pages.datasource.form.hostPlaceholder" })}
            onChange={(event) => patch("host", event.target.value)}
          />
          <FieldError match={Boolean(errors.host)} className="mt-1">
            {errors.host}
          </FieldError>
        </Field>
        <Field invalid={Boolean(errors.port)} className="!gap-0">
          <Input
            id="datasource-port"
            required
            size="small"
            variant="outlined"
            inputMode="numeric"
            value={values.port}
            aria-label={intl.formatMessage({ id: "pages.datasource.form.portPlaceholder" })}
            aria-invalid={Boolean(errors.port) || undefined}
            placeholder={intl.formatMessage({ id: "pages.datasource.form.portPlaceholder" })}
            onChange={(event) => patch("port", event.target.value)}
          />
          <FieldError match={Boolean(errors.port)} className="mt-1">
            {errors.port}
          </FieldError>
        </Field>
      </div>
    </div>
  );

  const databaseField = (
    <Field
      invalid={Boolean(errors.database)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-database" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.database" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-database"
          required
          size="small"
          variant="outlined"
          value={values.database}
          aria-invalid={Boolean(errors.database) || undefined}
          placeholder={intl.formatMessage({ id: "pages.datasource.form.databasePlaceholder" })}
          onChange={(event) => patch("database", event.target.value)}
        />
        <FieldError match={Boolean(errors.database)} className="mt-1">
          {errors.database}
        </FieldError>
      </div>
    </Field>
  );

  const accessIdentityField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.accessIdentity" })}
      </span>
      <Select
        size="small"
        items={{
          USERNAME_PASSWORD: intl.formatMessage({ id: "pages.datasource.form.usernamePassword" }),
        }}
        value="USERNAME_PASSWORD"
      >
        <SelectTrigger variant="outlined">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="USERNAME_PASSWORD">
            <SelectItemText>
              {intl.formatMessage({ id: "pages.datasource.form.usernamePassword" })}
            </SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        </SelectContent>
      </Select>
    </div>
  );

  const usernameField = (
    <Field
      invalid={Boolean(errors.username)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel required htmlFor="datasource-username" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.username" })}
      </FieldLabel>
      <div className="min-w-0">
        <Input
          id="datasource-username"
          required
          size="small"
          variant="outlined"
          value={values.username}
          aria-invalid={Boolean(errors.username) || undefined}
          placeholder={intl.formatMessage({
            id: "pages.datasource.form.usernamePlaceholder",
          })}
          onChange={(event) => patch("username", event.target.value)}
        />
        <FieldError match={Boolean(errors.username)} className="mt-1">
          {errors.username}
        </FieldError>
      </div>
    </Field>
  );

  const passwordField = (
    <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
      <FieldLabel htmlFor="datasource-password" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.password" })}
      </FieldLabel>
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
    </Field>
  );

  const authOptionField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.authOption" })}
      </span>
      <label className="flex min-h-7 items-center gap-2 text-xs text-[#344054]">
        <input
          type="radio"
          checked
          readOnly
          className="size-3.5 accent-[var(--yak-color-primary)]"
        />
        {intl.formatMessage({ id: "pages.datasource.form.noAuth" })}
      </label>
    </div>
  );

  const versionField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.version" })}
      </span>
      <Select
        size="small"
        items={{ AUTO: intl.formatMessage({ id: "pages.datasource.form.versionAuto" }) }}
        value="AUTO"
      >
        <SelectTrigger variant="outlined">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="AUTO">
            <SelectItemText>
              {intl.formatMessage({ id: "pages.datasource.form.versionAuto" })}
            </SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        </SelectContent>
      </Select>
    </div>
  );

  const advancedPropertiesField = (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[#344054]">
        {intl.formatMessage({ id: "pages.datasource.form.advancedProperties" })}
      </span>
      <Field invalid={Boolean(errors.properties)} className="min-w-0 !gap-0">
        <Button size="small" onClick={addProperty}>
          {intl.formatMessage({ id: "pages.datasource.form.addProperty" })}
        </Button>

        {values.properties.length > 0 ? (
          <div className="mt-2 overflow-hidden border border-[#e7e9ed]">
            <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_56px] bg-[#f5f5f5] text-xs font-medium text-[#344054]">
              <div className="px-2.5 py-2">
                {intl.formatMessage({ id: "pages.datasource.form.propertyKey" })}
              </div>
              <div className="px-2.5 py-2">
                {intl.formatMessage({ id: "pages.datasource.form.propertyValue" })}
              </div>
              <div className="px-2.5 py-2 text-center">
                {intl.formatMessage({ id: "pages.datasource.table.actions" })}
              </div>
            </div>
            {values.properties.map((property, index) => (
              <div
                key={"property-" + index}
                className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_56px] items-start gap-2 border-t border-[#eef0f3] p-2"
              >
                <Input
                  size="small"
                  variant="outlined"
                  value={property.key}
                  placeholder={intl.formatMessage({
                    id: "pages.datasource.form.propertyKeyPlaceholder",
                  })}
                  onChange={(event) => patchProperty(index, "key", event.target.value)}
                />
                <Input
                  size="small"
                  variant="outlined"
                  value={property.value}
                  placeholder={intl.formatMessage({
                    id: "pages.datasource.form.propertyValuePlaceholder",
                  })}
                  onChange={(event) => patchProperty(index, "value", event.target.value)}
                />
                <Button
                  size="small"
                  variant="ghost"
                  className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                  onClick={() => removeProperty(index)}
                >
                  {intl.formatMessage({ id: "pages.datasource.form.deleteProperty" })}
                </Button>
              </div>
            ))}
          </div>
        ) : null}
        <FieldError match={Boolean(errors.properties)} className="mt-1">
          {errors.properties}
        </FieldError>
      </Field>
    </div>
  );

  const connectionFields = (
    <>
      {jdbcPreviewField}
      {connectionAddressField}
      {databaseField}
      {accessIdentityField}
      {usernameField}
      {passwordField}
      {authOptionField}
      {versionField}
      {advancedPropertiesField}
    </>
  );

  const remarkField = (
    <Field
      invalid={Boolean(errors.remark)}
      className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
    >
      <FieldLabel htmlFor="datasource-remark" className="pt-1.5 text-xs leading-4">
        {intl.formatMessage({ id: "pages.datasource.form.description" })}
      </FieldLabel>
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
        <FieldError match={Boolean(errors.remark)} className="mt-1">
          {errors.remark}
        </FieldError>
      </div>
    </Field>
  );

  const selectedType = COMMON_DB_OPTIONS.find(
    (option) => option.value === normalizeDataSourceType(values.dbType),
  );
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

  const configContent = (
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
          {connectionFields}
        </div>
      </section>
    </div>
  );

  return (
    <Modal
      open={open}
      width={960}
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      title={
        !editing && createStep === "select"
          ? intl.formatMessage({ id: "pages.datasource.wizard.title" })
          : intl.formatMessage(
              {
                id: editing
                  ? "pages.datasource.wizard.editConfigTitle"
                  : "pages.datasource.wizard.configTitle",
              },
              { type: selectedType?.label || values.dbType },
            )
      }
      bodyClassName={createStep === "config" ? "py-3" : undefined}
      footer={
        !editing && createStep === "select" ? (
          <Button size="small" disabled={busy} onClick={() => onOpenChange(false)}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
        ) : (
          <div className="flex w-full items-center justify-between">
            <Button
              size="small"
              disabled={busy}
              onClick={() => {
                if (editing) onOpenChange(false);
                else setCreateStep("select");
              }}
            >
              {intl.formatMessage({
                id: editing
                  ? "pages.datasource.modal.button.cancel"
                  : "pages.datasource.wizard.back",
              })}
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
                {intl.formatMessage({
                  id: editing
                    ? "pages.datasource.modal.button.save"
                    : "pages.datasource.wizard.complete",
                })}
              </Button>
            </div>
          </div>
        )
      }
    >
      {!editing && createStep === "select" ? (
        <div className="flex h-[420px] flex-col">
          <section className="shrink-0">
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
        configContent
      )}
    </Modal>
  );
};

export default DataSourceForm;
