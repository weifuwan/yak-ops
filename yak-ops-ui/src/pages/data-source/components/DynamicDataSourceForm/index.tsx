import YakButton from '@/components/YakButton';
import { InfoCircleOutlined, LoadingOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import {
  Collapse,
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
  Tooltip,
} from 'antd';
import type { FormInstance } from 'antd';
import { Code2, FlaskConical, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect } from 'react';

import DatabaseIcons from '../../icon/DatabaseIcons';
import type {
  DynamicDataSourceFormProps,
  DynamicFormField,
  DynamicFormSection,
} from '../../types';
import { DataSourceOperateType } from '../../types';
import DriverManager from '../DriverManager';
import JdbcUrlField from '../JdbcUrlField';
import SshTunnelManager, {
  getSshTunnelValidationMessage,
} from '../SshTunnelManager';
import CustomKVList from './components/CustomKVList';
import { PLUGIN_CONFIG_STATUS } from './hooks/pluginConfigState';
import { usePluginFormConfig } from './hooks/usePluginFormConfig';
import {
  getFieldDefaultValue,
  getFieldDependencies,
  isDynamicFieldVisible,
  transformRules,
} from './utils/formUtils';

const DEFAULT_ENVIRONMENT = 'DEVELOP';

const sectionTitleClass = 'm-0 text-sm font-semibold leading-6 text-[#161823]';
const sectionDescriptionClass = 'm-0 text-xs leading-5 text-[#8a8f99]';

/** 字段进入隐藏态时清除历史值和校验错误，确保不会被提交。 */
const HiddenFieldCleaner = ({
  form,
  fieldKey,
}: {
  form: FormInstance;
  fieldKey: string;
}) => {
  useEffect(() => {
    form.setFields([{ name: fieldKey, value: undefined, errors: [] }]);
  }, [fieldKey, form]);
  return null;
};

/** 字段重新显示时，如果当前没有值，则恢复 Schema 默认值。 */
const VisibleFieldInitializer = ({
  form,
  field,
  children,
}: {
  form: FormInstance;
  field: DynamicFormField;
  children: ReactNode;
}) => {
  useEffect(() => {
    if (form.getFieldValue(field.key) !== undefined) return;
    const defaultValue = getFieldDefaultValue(field);
    if (defaultValue !== undefined) {
      form.setFieldValue(field.key, defaultValue);
    }
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
  const envOptions = [
    {
      value: 'DEVELOP',
      label: (
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-md bg-blue-50 text-blue-600">
            <Code2 size={12} />
          </span>
          <span className="text-[13px] text-[#344054]">
            {intl.formatMessage({
              id: 'pages.datasource.environment.developFull',
            })}
          </span>
        </div>
      ),
    },
    {
      value: 'TEST',
      label: (
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-md bg-amber-50 text-amber-600">
            <FlaskConical size={12} />
          </span>
          <span className="text-[13px] text-[#344054]">
            {intl.formatMessage({ id: 'pages.datasource.environment.testFull' })}
          </span>
        </div>
      ),
    },
    {
      value: 'PROD',
      label: (
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-md bg-rose-50 text-rose-600">
            <ShieldCheck size={12} />
          </span>
          <span className="text-[13px] text-[#344054]">
            {intl.formatMessage({ id: 'pages.datasource.environment.prodFull' })}
          </span>
        </div>
      ),
    },
  ];

  const {
    formSections,
    status: pluginStatus,
    message: pluginMessage,
    reload: reloadPluginConfig,
    installPlugin,
  } = usePluginFormConfig({
    dbType,
    configForm,
    initialConfig,
    resetOnLoad: true,
    intl,
  });

  useEffect(() => {
    if (operateType !== DataSourceOperateType.Create) return;
    const environment = form.getFieldValue('environment');
    if (environment === undefined || environment === null || environment === '') {
      form.setFieldValue('environment', DEFAULT_ENVIRONMENT);
    }
  }, [form, operateType]);

  const validateField = (key: string) => {
    window.setTimeout(() => {
      void configForm.validateFields([key]).catch(() => undefined);
    }, 0);
  };

  const renderFormControl = (field: DynamicFormField) => {
    switch (field.type) {
      case 'DRIVER':
        return <DriverManager dbType={dbType} placeholder={field.placeholder} />;
      case 'SSH':
        return <SshTunnelManager />;
      case 'JDBC_URL':
        return (
          <JdbcUrlField
            form={configForm}
            linkage={field.urlLinkage}
            placeholder={field.placeholder}
          />
        );
      case 'PASSWORD':
        return (
          <Input.Password
            variant="filled"
            placeholder={field.placeholder}
            onChange={() => validateField(field.key)}
          />
        );
      case 'SELECT':
        return (
          <Select
            variant="filled"
            placeholder={field.placeholder}
            options={field.options}
            onChange={() => validateField(field.key)}
          />
        );
      case 'NUMBER':
        return (
          <InputNumber
            variant="filled"
            className="!w-full"
            placeholder={field.placeholder}
            onChange={() => validateField(field.key)}
          />
        );
      case 'SWITCH':
        return <Switch onChange={() => validateField(field.key)} />;
      case 'TEXTAREA':
        return (
          <Input.TextArea
            variant="filled"
            rows={2}
            placeholder={field.placeholder}
            onChange={() => validateField(field.key)}
          />
        );
      default:
        return (
          <Input
            variant="filled"
            placeholder={field.placeholder}
            onChange={() => validateField(field.key)}
          />
        );
    }
  };

  const fieldRules = (field: DynamicFormField) => {
    const rules = transformRules(field.rules, field.type);
    if (field.type === 'SSH') {
      rules.push({
        validator: async (_rule, value) => {
          const validationMessage = getSshTunnelValidationMessage(value, intl);
          if (validationMessage) throw new Error(validationMessage);
        },
      });
    }
    return rules;
  };

  const renderVisibleField = (field: DynamicFormField) => {
    const content =
      field.type === 'CUSTOM_SELECT' ? (
        <div className="md:col-span-2">
          <CustomKVList intl={intl} field={field} />
        </div>
      ) : (
        <Form.Item
          label={field.label}
          name={field.key}
          preserve={false}
          valuePropName={field.type === 'SWITCH' ? 'checked' : 'value'}
          rules={fieldRules(field)}
          validateTrigger={['onChange', 'onBlur']}
          className={[
            '!mb-3',
            field.type === 'TEXTAREA' ||
            field.type === 'DRIVER' ||
            field.type === 'SSH' ||
            field.type === 'JDBC_URL'
              ? 'md:col-span-2'
              : '',
          ]
            .filter(Boolean)
            .join(' ')}
        >
          {renderFormControl(field)}
        </Form.Item>
      );

    return (
      <VisibleFieldInitializer
        key={field.key}
        form={configForm}
        field={field}
      >
        {content}
      </VisibleFieldInitializer>
    );
  };

  const renderField = (field: DynamicFormField) => {
    const hasVisibilityRule = Array.isArray(field.visibleWhen)
      ? field.visibleWhen.length > 0
      : Boolean(field.visibleWhen);
    if (!hasVisibilityRule) return renderVisibleField(field);

    const dependencies = getFieldDependencies(field);
    return (
      <Form.Item
        key={`visibility-${field.key}`}
        noStyle
        dependencies={dependencies.map((dependency) => dependency.split('.'))}
      >
        {({ getFieldsValue }) => {
          const visible = isDynamicFieldVisible(
            field,
            getFieldsValue(true) as Record<string, unknown>,
          );
          return visible ? (
            renderVisibleField(field)
          ) : (
            <HiddenFieldCleaner form={configForm} fieldKey={field.key} />
          );
        }}
      </Form.Item>
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
      {section.description && (
        <p className={sectionDescriptionClass}>{section.description}</p>
      )}
    </div>
  );

  const renderSchemaSection = (section: DynamicFormSection) => {
    if (section.collapsible) {
      return (
        <Collapse
          key={section.key}
          className="datasource-schema-collapse"
          bordered={false}
          defaultActiveKey={section.defaultExpanded === false ? [] : [section.key]}
          items={[
            {
              key: section.key,
              label: renderSectionHeader(section),
              children: renderFields(section.fields),
              forceRender: true,
            },
          ]}
        />
      );
    }

    return (
      <section key={section.key} className="datasource-schema-section">
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

    if (
      pluginStatus === PLUGIN_CONFIG_STATUS.LOADING ||
      pluginStatus === PLUGIN_CONFIG_STATUS.INSTALLING
    ) {
      return (
        <div className="mt-4 flex min-h-[96px] items-center justify-center rounded-lg border border-[#eef0f3] bg-[#fafbfc]">
          <div className="flex items-center gap-2 text-sm text-[#667085]">
            <LoadingOutlined />
            <span>
              {intl.formatMessage({
                id:
                  pluginStatus === PLUGIN_CONFIG_STATUS.INSTALLING
                    ? 'pages.datasource.plugin.installing'
                    : 'pages.datasource.plugin.loading',
              })}
            </span>
          </div>
        </div>
      );
    }

    const installRequired =
      pluginStatus === PLUGIN_CONFIG_STATUS.INSTALL_REQUIRED;
    return (
      <div className="mt-4 rounded-lg border border-[#e4e7ec] bg-[#fafafa] px-3.5 py-3">
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <div className="text-[13px] font-medium leading-5 text-[#344054]">
              {intl.formatMessage({
                id: installRequired
                  ? 'pages.datasource.plugin.installRequiredTitle'
                  : 'pages.datasource.plugin.loadFailedTitle',
              })}
            </div>
            <div className="mt-1 text-xs leading-5 text-[#98a2b3]">
              {pluginMessage ||
                intl.formatMessage({
                  id: installRequired
                    ? 'pages.datasource.plugin.installRequiredDescription'
                    : 'pages.datasource.plugin.loadFailedDescription',
                })}
            </div>
          </div>
          <YakButton
            size="small"
            type="text"
            className="shrink-0"
            onClick={() => {
              if (installRequired) {
                void installPlugin().then((installed) => {
                  if (installed) {
                    message.success(
                      intl.formatMessage({
                        id: 'pages.datasource.plugin.installSuccess',
                      }),
                    );
                  }
                });
                return;
              }
              void reloadPluginConfig();
            }}
          >
            <span className="inline-flex items-center gap-1.5">
              {intl.formatMessage({
                id: installRequired
                  ? 'pages.datasource.plugin.install'
                  : 'pages.datasource.plugin.reload',
              })}
              <DatabaseIcons dbType={dbType} height="15" width="15" />
            </span>
          </YakButton>
        </div>
      </div>
    );
  };

  return (
    <div className="bg-white">
      <section className="datasource-editor-base-section">
        <div className="mb-3 flex items-end justify-between gap-4">
          <h3 className={sectionTitleClass}>
            {intl.formatMessage({ id: 'pages.datasource.form.basicInfo' })}
          </h3>
          <div className="flex items-center gap-1.5 text-xs text-[#8a8f99]">
            <DatabaseIcons dbType={dbType} width="15" height="15" />
            <span>{dbType}</span>
          </div>
        </div>

        <Form form={form} layout="vertical" colon={false} requiredMark>
          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <Form.Item
              className="!mb-3"
              label={intl.formatMessage({ id: 'pages.datasource.form.dsName' })}
              name="name"
              rules={[
                {
                  required: true,
                  message: intl.formatMessage({
                    id: 'pages.datasource.form.dsNameRequired',
                  }),
                },
                {
                  max: 128,
                  message: intl.formatMessage({
                    id: 'pages.datasource.form.dsNameMax',
                  }),
                },
              ]}
            >
              <Input
                variant="filled"
                maxLength={128}
                placeholder={intl.formatMessage({
                  id: 'pages.datasource.form.dsNamePlaceholder',
                })}
              />
            </Form.Item>

            <Form.Item
              className="!mb-3"
              label={
                <span className="inline-flex items-center">
                  {intl.formatMessage({ id: 'pages.datasource.form.env' })}
                  <Tooltip
                    title={intl.formatMessage({
                      id: 'pages.datasource.form.envTooltip',
                    })}
                  >
                    <InfoCircleOutlined className="ml-1 text-[#98a2b3]" />
                  </Tooltip>
                </span>
              }
              name="environment"
              rules={[
                {
                  required: true,
                  message: intl.formatMessage({
                    id: 'pages.datasource.form.envRequired',
                  }),
                },
              ]}
            >
              <Select
                variant="filled"
                placeholder={intl.formatMessage({
                  id: 'pages.datasource.form.envPlaceholder',
                })}
                options={envOptions}
              />
            </Form.Item>
          </div>

          <Form.Item
            className="!mb-0"
            label={intl.formatMessage({
              id: 'pages.datasource.form.description',
            })}
            name="remark"
            rules={[
              {
                max: 500,
                message: intl.formatMessage({
                  id: 'pages.datasource.form.descriptionMax',
                }),
              },
            ]}
          >
            <Input.TextArea
              variant="filled"
              maxLength={500}
              rows={2}
              placeholder={intl.formatMessage({
                id: 'pages.datasource.form.descriptionPlaceholder',
              })}
            />
          </Form.Item>
        </Form>
      </section>

      {renderPluginState()}

      {formSections.length > 0 && (
        <Form
          form={configForm}
          component={false}
          layout="vertical"
          colon={false}
          requiredMark
        >
          <div className="datasource-schema-sections">
            {formSections.map(renderSchemaSection)}
          </div>
        </Form>
      )}
    </div>
  );
};

export default DynamicDataSourceForm;
