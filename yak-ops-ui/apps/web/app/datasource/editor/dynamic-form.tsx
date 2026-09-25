import {
  Button,
  Collapsible,
  CollapsiblePanel,
  CollapsibleTrigger,
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
  Spinner,
  Switch,
  Textarea,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@yak-ops/yak-ui";
import {
  ChevronDown,
  CircleHelp,
  Code2,
  FlaskConical,
  ShieldCheck,
} from "lucide-react";
import { useEffect, type ReactNode } from "react";

import JdbcUrlField from "./jdbc-url-field";
import SshTunnelManager, {
  getSshTunnelValidationMessage,
} from "./ssh-tunnel-manager";
import { useIntl } from "../i18n";
import {
  PLUGIN_CONFIG_STATUS,
  usePluginFormConfig,
} from "../hooks/use-plugin-form-config";
import { getEnvironmentTagConfigMap } from "../constants";
import DatabaseIcons from "../icons/DatabaseIcons";
import type { DynamicFormField, DynamicFormSection } from "../types";
import {
  DataSourceOperateType,
  type DynamicDataSourceFormProps,
} from "./types";
import {
  DataSourceFormField,
  DataSourceFormProvider,
  type DataSourceFormFieldState,
  type FormRule,
  useFormValues,
} from "./form-runtime";
import CustomKVList from "./custom-kv-list";
import {
  getFieldDefaultValue,
  isDynamicFieldVisible,
  transformRules,
} from "./form-utils";

const DEFAULT_ENVIRONMENT = "DEVELOP";

const sectionTitleClass = "m-0 text-sm font-semibold leading-6 text-[#161823]";
const sectionDescriptionClass = "m-0 text-xs leading-5 text-[#8a8f99]";

const FieldShell = ({
  children,
  className,
  error,
  label,
}: {
  children: ReactNode;
  className?: string;
  error?: string;
  label?: ReactNode;
}) => (
  <div className={className}>
    {label ? (
      <div className="mb-1.5 text-[13px] font-medium leading-5 text-[#344054]">
        {label}
      </div>
    ) : null}
    {children}
    {error ? (
      <div className="mt-1 text-[11px] leading-4 text-[#b42318]">{error}</div>
    ) : null}
  </div>
);

const HiddenFieldCleaner = ({
  form,
  fieldKey,
}: {
  form: DynamicDataSourceFormProps["configForm"];
  fieldKey: string;
}) => {
  useEffect(() => {
    form.setFields([{ name: fieldKey, value: undefined, errors: [] }]);
  }, [fieldKey, form]);
  return null;
};

const VisibleFieldInitializer = ({
  children,
  field,
  form,
}: {
  children: ReactNode;
  field: DynamicFormField;
  form: DynamicDataSourceFormProps["configForm"];
}) => {
  useEffect(() => {
    if (form.getFieldValue(field.key) !== undefined) return;
    const defaultValue = getFieldDefaultValue(field);
    if (defaultValue !== undefined) form.setFieldValue(field.key, defaultValue);
  }, [field, form]);
  return <>{children}</>;
};

const DynamicDataSourceForm = ({
  dbType,
  form,
  configForm,
  operateType,
  initialConfig,
}: DynamicDataSourceFormProps) => {
  const intl = useIntl();
  const configValues = useFormValues(configForm);
  const environmentTagConfig = getEnvironmentTagConfigMap(intl);
  const envOptions = ["DEVELOP", "TEST", "PROD"].map((value) => ({
    value,
    label: environmentTagConfig[value]?.text || value,
  }));

  const {
    formSections,
    status: pluginStatus,
    message: pluginMessage,
    reload: reloadPluginConfig,
  } = usePluginFormConfig({
    dbType,
    configForm,
    initialConfig,
    resetOnLoad: true,
    intl,
  });

  useEffect(() => {
    if (operateType !== DataSourceOperateType.Create) return;
    const environment = form.getFieldValue("environment");
    if (environment === undefined || environment === null || environment === "") {
      form.setFieldValue("environment", DEFAULT_ENVIRONMENT);
    }
  }, [form, operateType]);

  const fieldRules = (field: DynamicFormField): FormRule[] => {
    const rules = transformRules(field.rules, field.type);
    if (field.type === "SSH") {
      rules.push({
        validator: async (value) => {
          const validationMessage = getSshTunnelValidationMessage(
            value as Parameters<typeof getSshTunnelValidationMessage>[0],
            intl,
          );
          if (validationMessage) throw new Error(validationMessage);
        },
      });
    }
    return rules;
  };

  const renderFormControl = (
    field: DynamicFormField,
    state: DataSourceFormFieldState,
  ) => {
    const { invalid, setValue, validate, value } = state;
    const validateLater = () => {
      window.setTimeout(() => void validate().catch(() => undefined), 0);
    };

    switch (field.type) {
      case "SSH":
        return (
          <SshTunnelManager
            value={value as Parameters<typeof getSshTunnelValidationMessage>[0]}
            onChange={(next) => {
              setValue(next);
              validateLater();
            }}
          />
        );
      case "JDBC_URL":
        return (
          <JdbcUrlField
            form={configForm}
            value={String(value ?? "")}
            linkage={field.urlLinkage}
            placeholder={field.placeholder}
            onChange={(next) => {
              setValue(next);
              validateLater();
            }}
          />
        );
      case "PASSWORD":
        return (
          <PasswordInput
            value={String(value ?? "")}
            aria-invalid={invalid || undefined}
            placeholder={field.placeholder}
            onChange={(event) => setValue(event.target.value)}
            onBlur={() => void validate().catch(() => undefined)}
          />
        );
      case "SELECT":
        return (
          <Select
            value={value == null ? null : String(value)}
            onValueChange={(next) => {
              setValue(next);
              validateLater();
            }}
          >
            <SelectTrigger aria-invalid={invalid || undefined}>
              <SelectValue placeholder={field.placeholder} />
            </SelectTrigger>
            <SelectContent>
              {(field.options || []).map((option) => (
                <SelectItem key={String(option.value)} value={String(option.value)}>
                  <SelectItemText>{option.label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );
      case "NUMBER":
        return (
          <NumberField
            value={typeof value === "number" ? value : null}
            onValueChange={(next) => {
              setValue(next);
              validateLater();
            }}
          >
            <NumberFieldGroup>
              <NumberFieldInput
                aria-invalid={invalid || undefined}
                placeholder={field.placeholder}
              />
            </NumberFieldGroup>
          </NumberField>
        );
      case "SWITCH":
        return (
          <Switch
            checked={Boolean(value)}
            onCheckedChange={(next) => {
              setValue(next);
              validateLater();
            }}
          />
        );
      case "TEXTAREA":
        return (
          <Textarea
            rows={2}
            value={String(value ?? "")}
            aria-invalid={invalid || undefined}
            placeholder={field.placeholder}
            onValueChange={(next) => setValue(next)}
            onBlur={() => void validate().catch(() => undefined)}
          />
        );
      default:
        return (
          <Input
            value={String(value ?? "")}
            aria-invalid={invalid || undefined}
            placeholder={field.placeholder}
            onChange={(event) => setValue(event.target.value)}
            onBlur={() => void validate().catch(() => undefined)}
          />
        );
    }
  };

  const renderVisibleField = (field: DynamicFormField) => {
    if (field.type === "CUSTOM_SELECT") {
      return (
        <VisibleFieldInitializer key={field.key} form={configForm} field={field}>
          <div className="md:col-span-2">
            <CustomKVList intl={intl} field={field} />
          </div>
        </VisibleFieldInitializer>
      );
    }

    const wide =
      field.type === "TEXTAREA" ||
      field.type === "SSH" ||
      field.type === "JDBC_URL";

    return (
      <VisibleFieldInitializer key={field.key} form={configForm} field={field}>
        <DataSourceFormField name={field.key} rules={fieldRules(field)}>
          {(state) => (
            <FieldShell
              label={field.label}
              error={state.error}
              className={wide ? "mb-3 md:col-span-2" : "mb-3"}
            >
              {renderFormControl(field, state)}
            </FieldShell>
          )}
        </DataSourceFormField>
      </VisibleFieldInitializer>
    );
  };

  const renderField = (field: DynamicFormField) => {
    const hasVisibilityRule = Array.isArray(field.visibleWhen)
      ? field.visibleWhen.length > 0
      : Boolean(field.visibleWhen);

    if (!hasVisibilityRule) return renderVisibleField(field);

    const visible = isDynamicFieldVisible(field, configValues);
    return visible ? (
      renderVisibleField(field)
    ) : (
      <HiddenFieldCleaner
        key={`hidden-${field.key}`}
        form={configForm}
        fieldKey={field.key}
      />
    );
  };

  const renderFields = (fields: DynamicFormField[]) => (
    <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
      {fields.map(renderField)}
    </div>
  );

  const renderSectionHeader = (section: DynamicFormSection) => (
    <div className="min-w-0">
      <h3 className={sectionTitleClass}>{section.title}</h3>
      {section.description ? (
        <p className={sectionDescriptionClass}>{section.description}</p>
      ) : null}
    </div>
  );

  const renderSchemaSection = (section: DynamicFormSection) => {
    if (section.collapsible) {
      return (
        <Collapsible
          key={section.key}
          defaultOpen={section.defaultExpanded !== false}
          className="border-b border-[#eef0f3] py-3"
        >
          <CollapsibleTrigger className="flex w-full cursor-pointer items-center justify-between gap-3 text-left">
            {renderSectionHeader(section)}
            <ChevronDown size={16} className="shrink-0 text-[#98a2b3]" />
          </CollapsibleTrigger>
          <CollapsiblePanel className="pt-3">
            {renderFields(section.fields)}
          </CollapsiblePanel>
        </Collapsible>
      );
    }

    return (
      <section key={section.key} className="border-b border-[#eef0f3] py-4 last:border-b-0">
        <div className="mb-3">{renderSectionHeader(section)}</div>
        {renderFields(section.fields)}
      </section>
    );
  };

  const renderPluginState = () => {
    if (
      pluginStatus === PLUGIN_CONFIG_STATUS.IDLE ||
      pluginStatus === PLUGIN_CONFIG_STATUS.READY
    ) {
      return null;
    }

    if (pluginStatus === PLUGIN_CONFIG_STATUS.LOADING) {
      return (
        <div className="mt-4 flex min-h-[96px] items-center justify-center rounded-lg border border-[#eef0f3] bg-[#fafbfc]">
          <div className="flex items-center gap-2 text-sm text-[#667085]">
            <Spinner label="Loading plugin configuration" />
            <span>
              {intl.formatMessage({
                id: "pages.datasource.plugin.loading",
              })}
            </span>
          </div>
        </div>
      );
    }

    return (
      <div className="mt-4 rounded-lg border border-[#e4e7ec] bg-[#fafafa] px-3.5 py-3">
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <div className="text-[13px] font-medium leading-5 text-[#344054]">
              {intl.formatMessage({
                id: "pages.datasource.plugin.loadFailedTitle",
              })}
            </div>
            <div className="mt-1 text-xs leading-5 text-[#98a2b3]">
              {pluginMessage ||
                intl.formatMessage({
                  id: "pages.datasource.plugin.loadFailedDescription",
                })}
            </div>
          </div>
          <Button
            size="small"
            variant="ghost"
            className="shrink-0"
            onClick={() => void reloadPluginConfig()}
          >
            <span className="inline-flex items-center gap-1.5">
              {intl.formatMessage({
                id: "pages.datasource.plugin.reload",
              })}
              <DatabaseIcons dbType={dbType} height="15" width="15" />
            </span>
          </Button>
        </div>
      </div>
    );
  };

  return (
    <div className="bg-white">
      <DataSourceFormProvider form={form}>
        <section className="border-b border-[#eef0f3] pb-4">
          <div className="mb-3 flex items-end justify-between gap-4">
            <h3 className={sectionTitleClass}>
              {intl.formatMessage({ id: "pages.datasource.form.basicInfo" })}
            </h3>
            <div className="flex items-center gap-1.5 text-xs text-[#8a8f99]">
              <DatabaseIcons dbType={dbType} width="15" height="15" />
              <span>{dbType}</span>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <DataSourceFormField
              name="name"
              rules={[
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
              ]}
            >
              {(state) => (
                <FieldShell
                  className="mb-3"
                  label={intl.formatMessage({ id: "pages.datasource.form.dsName" })}
                  error={state.error}
                >
                  <Input
                    maxLength={128}
                    value={String(state.value ?? "")}
                    aria-invalid={state.invalid || undefined}
                    placeholder={intl.formatMessage({
                      id: "pages.datasource.form.dsNamePlaceholder",
                    })}
                    onChange={(event) => state.setValue(event.target.value)}
                    onBlur={() => void state.validate().catch(() => undefined)}
                  />
                </FieldShell>
              )}
            </DataSourceFormField>

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
                  className="mb-3"
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

      {renderPluginState()}

      {formSections.length > 0 ? (
        <DataSourceFormProvider form={configForm}>
          <div className="mt-4">{formSections.map(renderSchemaSection)}</div>
        </DataSourceFormProvider>
      ) : null}
    </div>
  );
};

export default DynamicDataSourceForm;
