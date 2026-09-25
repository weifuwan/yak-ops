import {
  Input,
  NumberField,
  NumberFieldGroup,
  NumberFieldInput,
  PasswordInput,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@yak-ops/yak-ui";
import { CircleHelp } from "lucide-react";
import { useEffect, type ReactNode } from "react";

import { getEnvironmentTagConfigMap } from "../constants";
import { useIntl } from "../i18n";
import type { SshTunnelConfigValue } from "../types";
import CustomKVList from "./custom-kv-list";
import {
  DataSourceFormField,
  DataSourceFormProvider,
  type DataSourceFormFieldState,
  type FormRule,
} from "./form-runtime";
import JdbcUrlField from "./jdbc-url-field";
import SshTunnelManager, {
  getSshTunnelValidationMessage,
} from "./ssh-tunnel-manager";
import {
  DataSourceOperateType,
  type DataSourceConnectionFormProps,
} from "./types";

const DEFAULT_ENVIRONMENT = "DEVELOP";

interface ConnectionProfile {
  port: number;
  driverClassName: string;
  jdbcUrlTemplate: string;
  databaseLabelId: string;
}

const CONNECTION_PROFILES: Record<string, ConnectionProfile> = {
  MYSQL: {
    port: 3306,
    driverClassName: "com.mysql.cj.jdbc.Driver",
    jdbcUrlTemplate: "jdbc:mysql://{host}:{port}/{database}",
    databaseLabelId: "pages.datasource.form.database",
  },
  ORACLE: {
    port: 1521,
    driverClassName: "oracle.jdbc.OracleDriver",
    jdbcUrlTemplate: "jdbc:oracle:thin:@//{host}:{port}/{database}",
    databaseLabelId: "pages.datasource.form.oracleDatabase",
  },
  POSTGRE_SQL: {
    port: 5432,
    driverClassName: "org.postgresql.Driver",
    jdbcUrlTemplate: "jdbc:postgresql://{host}:{port}/{database}",
    databaseLabelId: "pages.datasource.form.database",
  },
};

const SSH_DEFAULTS: SshTunnelConfigValue = {
  enabled: false,
  host: "",
  port: 22,
  username: "",
  authType: "PASSWORD",
  password: "",
  privateKey: "",
  passphrase: "",
  strictHostKeyChecking: false,
  knownHosts: "",
};

const normalizeDbType = (value: string) => {
  const normalized = value.trim().toUpperCase().replaceAll("-", "_");
  if (normalized === "POSTGRES" || normalized === "POSTGRESQL") {
    return "POSTGRE_SQL";
  }
  return normalized;
};

const stringValue = (value: unknown, fallback = "") =>
  typeof value === "string" ? value : fallback;

const numberValue = (value: unknown, fallback: number) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

const propertyRows = (value: unknown) => {
  if (Array.isArray(value)) {
    return value.map((item) => {
      const row = item && typeof item === "object"
        ? item as Record<string, unknown>
        : {};
      return {
        key: String(row.key ?? ""),
        value: String(row.value ?? ""),
      };
    });
  }
  if (!value || typeof value !== "object") return [];
  return Object.entries(value as Record<string, unknown>).map(([key, itemValue]) => ({
    key,
    value: itemValue == null ? "" : String(itemValue),
  }));
};

const FieldShell = ({
  children,
  error,
  label,
}: {
  children: ReactNode;
  error?: string;
  label: ReactNode;
}) => (
  <div className="mb-3">
    <div className="mb-1.5 text-[13px] font-medium leading-5 text-[#344054]">
      {label}
    </div>
    {children}
    {error ? (
      <div className="mt-1 text-[11px] leading-4 text-[#b42318]">{error}</div>
    ) : null}
  </div>
);

const requiredRule = (message: string): FormRule[] => [
  { required: true, message },
];

const DataSourceConnectionForm = ({
  dbType,
  form,
  configForm,
  operateType,
  initialConfig,
}: DataSourceConnectionFormProps) => {
  const intl = useIntl();
  const environmentTagConfig = getEnvironmentTagConfigMap(intl);
  const profile =
    CONNECTION_PROFILES[normalizeDbType(dbType)] ?? CONNECTION_PROFILES.MYSQL;

  useEffect(() => {
    if (operateType !== DataSourceOperateType.Create) return;
    if (!form.getFieldValue("environment")) {
      form.setFieldValue("environment", DEFAULT_ENVIRONMENT);
    }
  }, [form, operateType]);

  useEffect(() => {
    const initial = initialConfig ?? {};
    const initialSsh =
      initial.sshTunnel && typeof initial.sshTunnel === "object"
        ? initial.sshTunnel as Partial<SshTunnelConfigValue>
        : {};

    configForm.setFieldsValue({
      host: stringValue(initial.host, "127.0.0.1"),
      port: numberValue(initial.port, profile.port),
      database: stringValue(initial.database),
      schema: stringValue(initial.schema),
      username: stringValue(initial.username),
      password: stringValue(initial.password),
      jdbcUrl: stringValue(initial.jdbcUrl),
      sshTunnel: { ...SSH_DEFAULTS, ...initialSsh },
      driverClassName: stringValue(
        initial.driverClassName,
        profile.driverClassName,
      ),
      properties: propertyRows(initial.properties),
    });
  }, [configForm, initialConfig, profile]);

  const envOptions = ["DEVELOP", "TEST", "PROD"].map((value) => ({
    value,
    label: environmentTagConfig[value]?.text || value,
  }));

  const renderInputField = (
    name: string,
    label: ReactNode,
    placeholder: string,
    rules: FormRule[] = [],
    password = false,
  ) => (
    <DataSourceFormField name={name} rules={rules}>
      {(state: DataSourceFormFieldState) => (
        <FieldShell label={label} error={state.error}>
          {password ? (
            <PasswordInput
              value={String(state.value ?? "")}
              aria-invalid={state.invalid || undefined}
              placeholder={placeholder}
              onChange={(event) => state.setValue(event.target.value)}
              onBlur={() => void state.validate().catch(() => undefined)}
            />
          ) : (
            <Input
              value={String(state.value ?? "")}
              aria-invalid={state.invalid || undefined}
              placeholder={placeholder}
              onChange={(event) => state.setValue(event.target.value)}
              onBlur={() => void state.validate().catch(() => undefined)}
            />
          )}
        </FieldShell>
      )}
    </DataSourceFormField>
  );

  return (
    <div className="bg-white">
      <DataSourceFormProvider form={form}>
        <section className="border-b border-[#eef0f3] pb-4">
          <h3 className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
            {intl.formatMessage({ id: "pages.datasource.form.basicInfo" })}
          </h3>

          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            {renderInputField(
              "name",
              intl.formatMessage({ id: "pages.datasource.form.dsName" }),
              intl.formatMessage({
                id: "pages.datasource.form.dsNamePlaceholder",
              }),
              [
                {
                  required: true,
                  message: intl.formatMessage({
                    id: "pages.datasource.form.dsNameRequired",
                  }),
                },
                {
                  max: 128,
                  message: intl.formatMessage({
                    id: "pages.datasource.form.dsNameMax",
                  }),
                },
              ],
            )}

            <DataSourceFormField
              name="environment"
              rules={[
                {
                  required: true,
                  message: intl.formatMessage({
                    id: "pages.datasource.form.envRequired",
                  }),
                },
              ]}
            >
              {(state) => (
                <FieldShell
                  error={state.error}
                  label={
                    <span className="inline-flex items-center gap-1">
                      {intl.formatMessage({ id: "pages.datasource.form.env" })}
                      <Tooltip>
                        <TooltipTrigger
                          aria-label={intl.formatMessage({
                            id: "pages.datasource.form.envTooltip",
                          })}
                          className="inline-flex cursor-help"
                        >
                          <CircleHelp size={13} className="text-[#98a2b3]" />
                        </TooltipTrigger>
                        <TooltipContent>
                          {intl.formatMessage({
                            id: "pages.datasource.form.envTooltip",
                          })}
                        </TooltipContent>
                      </Tooltip>
                    </span>
                  }
                >
                  <Select
                    value={state.value == null ? null : String(state.value)}
                    onValueChange={(next) => state.setValue(next)}
                  >
                    <SelectTrigger aria-invalid={state.invalid || undefined}>
                      <SelectValue
                        placeholder={intl.formatMessage({
                          id: "pages.datasource.form.envPlaceholder",
                        })}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {envOptions.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          <SelectItemText>{option.label}</SelectItemText>
                          <SelectItemIndicator />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FieldShell>
              )}
            </DataSourceFormField>
          </div>

          <DataSourceFormField
            name="remark"
            rules={[
              {
                max: 500,
                message: intl.formatMessage({
                  id: "pages.datasource.form.descriptionMax",
                }),
              },
            ]}
          >
            {(state) => (
              <FieldShell
                label={intl.formatMessage({
                  id: "pages.datasource.form.description",
                })}
                error={state.error}
              >
                <Textarea
                  maxLength={500}
                  rows={2}
                  value={String(state.value ?? "")}
                  aria-invalid={state.invalid || undefined}
                  placeholder={intl.formatMessage({
                    id: "pages.datasource.form.descriptionPlaceholder",
                  })}
                  onValueChange={(next) => state.setValue(next)}
                  onBlur={() => void state.validate().catch(() => undefined)}
                />
              </FieldShell>
            )}
          </DataSourceFormField>
        </section>
      </DataSourceFormProvider>

      <DataSourceFormProvider form={configForm}>
        <section className="border-b border-[#eef0f3] py-4">
          <h3 className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
            {intl.formatMessage({
              id: "pages.datasource.form.connectionParams",
            })}
          </h3>

          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            {renderInputField(
              "host",
              intl.formatMessage({ id: "pages.datasource.form.host" }),
              intl.formatMessage({ id: "pages.datasource.form.hostPlaceholder" }),
              requiredRule(
                intl.formatMessage({ id: "pages.datasource.form.hostRequired" }),
              ),
            )}

            <DataSourceFormField
              name="port"
              rules={[
                {
                  required: true,
                  type: "number",
                  min: 1,
                  max: 65535,
                  message: intl.formatMessage({
                    id: "pages.datasource.form.portInvalid",
                  }),
                },
              ]}
            >
              {(state) => (
                <FieldShell
                  label={intl.formatMessage({ id: "pages.datasource.form.port" })}
                  error={state.error}
                >
                  <NumberField
                    min={1}
                    max={65535}
                    value={typeof state.value === "number" ? state.value : null}
                    onValueChange={(next) => state.setValue(next)}
                  >
                    <NumberFieldGroup>
                      <NumberFieldInput
                        aria-invalid={state.invalid || undefined}
                        placeholder={String(profile.port)}
                      />
                    </NumberFieldGroup>
                  </NumberField>
                </FieldShell>
              )}
            </DataSourceFormField>

            {renderInputField(
              "database",
              intl.formatMessage({ id: profile.databaseLabelId }),
              intl.formatMessage({
                id: "pages.datasource.form.databasePlaceholder",
              }),
              requiredRule(
                intl.formatMessage({
                  id: "pages.datasource.form.databaseRequired",
                }),
              ),
            )}

            {renderInputField(
              "schema",
              intl.formatMessage({ id: "pages.datasource.form.schema" }),
              intl.formatMessage({
                id: "pages.datasource.form.schemaPlaceholder",
              }),
            )}

            {renderInputField(
              "username",
              intl.formatMessage({ id: "pages.datasource.form.username" }),
              intl.formatMessage({
                id: "pages.datasource.form.usernamePlaceholder",
              }),
              requiredRule(
                intl.formatMessage({
                  id: "pages.datasource.form.usernameRequired",
                }),
              ),
            )}

            {renderInputField(
              "password",
              intl.formatMessage({ id: "pages.datasource.form.password" }),
              intl.formatMessage({
                id: "pages.datasource.form.passwordPlaceholder",
              }),
              [],
              true,
            )}
          </div>

          <DataSourceFormField name="jdbcUrl">
            {(state) => (
              <FieldShell
                label={intl.formatMessage({
                  id: "pages.datasource.form.jdbcUrl",
                })}
                error={state.error}
              >
                <JdbcUrlField
                  form={configForm}
                  value={String(state.value ?? "")}
                  linkage={{
                    template: profile.jdbcUrlTemplate,
                    hostField: "host",
                    portField: "port",
                    databaseField: "database",
                    preserveSuffix: true,
                  }}
                  placeholder={intl.formatMessage({
                    id: "pages.datasource.form.jdbcUrlPlaceholder",
                  })}
                  onChange={(next) => state.setValue(next)}
                />
              </FieldShell>
            )}
          </DataSourceFormField>
        </section>

        <section className="border-b border-[#eef0f3] py-4">
          <h3 className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
            {intl.formatMessage({ id: "pages.datasource.form.sshSection" })}
          </h3>
          <DataSourceFormField
            name="sshTunnel"
            rules={[
              {
                validator: async (value) => {
                  const message = getSshTunnelValidationMessage(
                    value as SshTunnelConfigValue | undefined,
                    intl,
                  );
                  if (message) throw new Error(message);
                },
              },
            ]}
          >
            {(state) => (
              <div className="mb-3">
                <SshTunnelManager
                  value={state.value as SshTunnelConfigValue | undefined}
                  onChange={(next) => state.setValue(next)}
                />
                {state.error ? (
                  <div className="mt-1 text-[11px] leading-4 text-[#b42318]">
                    {state.error}
                  </div>
                ) : null}
              </div>
            )}
          </DataSourceFormField>
        </section>

        <section className="py-4">
          <h3 className="mb-3 text-sm font-semibold leading-6 text-[#161823]">
            {intl.formatMessage({ id: "pages.datasource.form.advanced" })}
          </h3>

          {renderInputField(
            "driverClassName",
            intl.formatMessage({ id: "pages.datasource.form.driverClassName" }),
            profile.driverClassName,
            requiredRule(
              intl.formatMessage({
                id: "pages.datasource.form.driverClassNameRequired",
              }),
            ),
          )}

          <CustomKVList
            intl={intl}
            name="properties"
            label={intl.formatMessage({
              id: "pages.datasource.form.properties",
            })}
            placeholder={intl.formatMessage({
              id: "pages.datasource.form.propertiesPlaceholder",
            })}
          />
        </section>
      </DataSourceFormProvider>
    </div>
  );
};

export default DataSourceConnectionForm;
