import { useIntl } from '@umijs/max';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
} from 'antd';
import { useEffect } from 'react';
import { formatQualityDimension } from '../i18n';
import type {
  ComparisonOperator,
  SaveCustomTemplatePayload,
  TemplateFolderView,
  TemplateView,
} from '../types';

type DrawerMode = 'create' | 'edit';

interface CustomTemplateDrawerProps {
  open: boolean;
  mode: DrawerMode;
  template?: TemplateView;
  folders: TemplateFolderView[];
  defaultFolderId?: number;
  submitting?: boolean;
  onClose: () => void;
  onSubmit: (payload: SaveCustomTemplatePayload) => void;
}

type FormValues = SaveCustomTemplatePayload;

const DIMENSIONS = [
  '完整性',
  '唯一性',
  '有效性',
  '准确性',
  '一致性',
  '及时性',
  '规范性',
  '自定义',
];

const parseDefaults = (schema?: string) => {
  try {
    return schema ? JSON.parse(schema) : {};
  } catch {
    return {};
  }
};

const folderOptions = (folders: TemplateFolderView[]) => {
  const children = new Map<number | undefined, TemplateFolderView[]>();
  folders.forEach((folder) => {
    const items = children.get(folder.parentId) || [];
    items.push(folder);
    children.set(folder.parentId, items);
  });

  const result: Array<{ value: number; label: string }> = [];
  const walk = (parentId: number | undefined, depth: number) => {
    (children.get(parentId) || []).forEach((folder) => {
      result.push({
        value: folder.id,
        label: `${'　'.repeat(depth)}${folder.name}`,
      });
      walk(folder.id, depth + 1);
    });
  };
  walk(undefined, 0);
  return result;
};

const CustomTemplateDrawer = ({
  open,
  mode,
  template,
  folders,
  defaultFolderId,
  submitting,
  onClose,
  onSubmit,
}: CustomTemplateDrawerProps) => {
  const intl = useIntl();
  const [form] = Form.useForm<FormValues>();
  const operator = Form.useWatch('defaultOperator', form);
  const operators: Array<{ value: ComparisonOperator; label: string }> = [
    {
      value: 'GT',
      label: intl.formatMessage({ id: 'pages.dataQuality.template.operator.gt' }),
    },
    {
      value: 'GTE',
      label: intl.formatMessage({ id: 'pages.dataQuality.template.operator.gte' }),
    },
    {
      value: 'EQ',
      label: intl.formatMessage({ id: 'pages.dataQuality.template.operator.eq' }),
    },
    {
      value: 'LTE',
      label: intl.formatMessage({ id: 'pages.dataQuality.template.operator.lte' }),
    },
    {
      value: 'LT',
      label: intl.formatMessage({ id: 'pages.dataQuality.template.operator.lt' }),
    },
    {
      value: 'BETWEEN',
      label: intl.formatMessage({
        id: 'pages.dataQuality.template.operator.between',
      }),
    },
  ];

  useEffect(() => {
    if (!open) return;
    const defaults = parseDefaults(template?.parameterSchema);
    form.setFieldsValue({
      name: template?.name || '',
      description: template?.description || '',
      dimension: template?.dimension || '自定义',
      folderId: template?.folderId || defaultFolderId,
      setFlag: template?.setFlag || '',
      checkType: 'NUMERIC',
      checkMethod: 'FIXED_VALUE',
      customSql:
        template?.templateSql ||
        'SELECT COUNT(*) AS metric_value FROM ${tableName} WHERE ${where}',
      defaultOperator: defaults.defaultOperator || 'EQ',
      defaultThreshold: defaults.defaultThreshold ?? 0,
      defaultThresholdEnd: defaults.defaultThresholdEnd,
    });
  }, [defaultFolderId, form, open, template]);

  return (
    <Drawer
      width={620}
      title={intl.formatMessage({
        id:
          mode === 'create'
            ? 'pages.dataQuality.template.drawer.createTitle'
            : 'pages.dataQuality.template.drawer.editTitle',
      })}
      open={open}
      onClose={onClose}
      destroyOnClose
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>
            {intl.formatMessage({ id: 'pages.dataQuality.common.cancel' })}
          </Button>
          <Button
            type="primary"
            loading={submitting}
            onClick={() => form.validateFields().then(onSubmit)}
          >
            {intl.formatMessage({
              id:
                mode === 'create'
                  ? 'pages.dataQuality.template.drawer.create'
                  : 'pages.dataQuality.template.drawer.save',
            })}
          </Button>
        </div>
      }
    >
      <Alert
        type="info"
        showIcon
        className="mb-5"
        message={intl.formatMessage({ id: 'pages.dataQuality.template.drawer.info' })}
      />

      <Form<FormValues>
        form={form}
        layout="vertical"
        requiredMark="optional"
        initialValues={{
          dimension: '自定义',
          checkType: 'NUMERIC',
          checkMethod: 'FIXED_VALUE',
          defaultOperator: 'EQ',
          defaultThreshold: 0,
        }}
      >
        <Form.Item
          label={intl.formatMessage({ id: 'pages.dataQuality.template.drawer.name' })}
          name="name"
          rules={[
            {
              required: true,
              whitespace: true,
              message: intl.formatMessage({
                id: 'pages.dataQuality.template.drawer.nameRequired',
              }),
            },
          ]}
        >
          <Input
            variant="filled"
            maxLength={100}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.namePlaceholder',
            })}
          />
        </Form.Item>

        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.dimension',
            })}
            name="dimension"
            rules={[{ required: true }]}
          >
            <Select
              variant="filled"
              options={DIMENSIONS.map((value) => ({
                value,
                label: formatQualityDimension(intl, value),
              }))}
            />
          </Form.Item>
          <Form.Item
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.folder',
            })}
            name="folderId"
          >
            <Select
              allowClear
              variant="filled"
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.template.dialog.root',
              })}
              options={folderOptions(folders)}
            />
          </Form.Item>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Form.Item
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.checkType',
            })}
            name="checkType"
            rules={[{ required: true }]}
          >
            <Select
              variant="filled"
              disabled
              options={[
                {
                  value: 'NUMERIC',
                  label: intl.formatMessage({
                    id: 'pages.dataQuality.template.drawer.numeric',
                  }),
                },
              ]}
            />
          </Form.Item>
          <Form.Item
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.checkMethod',
            })}
            name="checkMethod"
            rules={[{ required: true }]}
          >
            <Select
              variant="filled"
              disabled
              options={[
                {
                  value: 'FIXED_VALUE',
                  label: intl.formatMessage({
                    id: 'pages.dataQuality.template.drawer.fixedValue',
                  }),
                },
              ]}
            />
          </Form.Item>
        </div>

        <Form.Item
          label="Set Flag"
          name="setFlag"
          extra={intl.formatMessage({
            id: 'pages.dataQuality.template.drawer.setFlagExtra',
          })}
        >
          <Input.TextArea
            variant="filled"
            rows={2}
            maxLength={1000}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.setFlagPlaceholder',
            })}
          />
        </Form.Item>

        <Form.Item
          label={intl.formatMessage({ id: 'pages.dataQuality.template.drawer.sql' })}
          name="customSql"
          rules={[
            {
              required: true,
              whitespace: true,
              message: intl.formatMessage({
                id: 'pages.dataQuality.template.drawer.sqlRequired',
              }),
            },
          ]}
          extra={intl.formatMessage({
            id: 'pages.dataQuality.template.drawer.sqlExtra',
          })}
        >
          <Input.TextArea
            variant="filled"
            rows={8}
            maxLength={20000}
            placeholder="SELECT COUNT(*) AS metric_value FROM ${tableName} WHERE ${where}"
            className="font-mono"
          />
        </Form.Item>

        <Form.Item
          label={intl.formatMessage({
            id: 'pages.dataQuality.template.drawer.defaultCondition',
          })}
          required
        >
          <Space.Compact block>
            <Form.Item
              name="defaultOperator"
              noStyle
              rules={[{ required: true }]}
            >
              <Select className="w-[190px]" variant="filled" options={operators} />
            </Form.Item>
            <Form.Item
              name="defaultThreshold"
              noStyle
              rules={[
                {
                  required: true,
                  message: intl.formatMessage({
                    id: 'pages.dataQuality.template.drawer.defaultThresholdRequired',
                  }),
                },
              ]}
            >
              <InputNumber
                className="!w-full"
                variant="filled"
                placeholder={intl.formatMessage({
                  id: 'pages.dataQuality.template.drawer.defaultThreshold',
                })}
              />
            </Form.Item>
            {operator === 'BETWEEN' ? (
              <Form.Item
                name="defaultThresholdEnd"
                noStyle
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.dataQuality.template.drawer.rangeMaxRequired',
                    }),
                  },
                ]}
              >
                <InputNumber
                  className="!w-full"
                  variant="filled"
                  placeholder={intl.formatMessage({
                    id: 'pages.dataQuality.template.drawer.rangeMax',
                  })}
                />
              </Form.Item>
            ) : null}
          </Space.Compact>
        </Form.Item>

        <Form.Item
          label={intl.formatMessage({
            id: 'pages.dataQuality.template.drawer.description',
          })}
          name="description"
        >
          <Input.TextArea
            variant="filled"
            rows={3}
            maxLength={500}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.template.drawer.descriptionPlaceholder',
            })}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default CustomTemplateDrawer;
