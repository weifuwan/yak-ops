import EmojiIconPicker, {
  DEFAULT_EMOJI_ICON,
  type EmojiIconValue,
} from '@/components/EmojiIconPicker';
import { YakButton } from '@/components/ui';
import {
  createOfflineSyncDraft,
  getOfflineSyncUniqueId,
} from '@/services/batch-link-up';
import {
  ArrowRightOutlined,
  DatabaseOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import {
  ConfigProvider,
  Drawer,
  Form,
  Input,
  Radio,
  Select,
  message,
} from 'antd';
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';

import {
  BRAND_COLOR,
  BRAND_COLOR_BORDER,
  BRAND_COLOR_SOFT,
  BRAND_COLOR_SOFT_HOVER,
  BRAND_THEME,
} from '@/styles/brand';

import { generateDataSourceOptions } from '../DataSourceSelect';
import { connectorIdForDataSourceType } from '../detail/form-schema/valueAdapter';
import {
  buildCreatePayload,
  type CreateSyncEndpoint,
  type CreateSyncTaskValues,
  type SyncMode,
} from '../detail/model';

interface CreateSyncTaskDrawerProps {
  open: boolean;
  onCancel: () => void;
  onCreated: (taskId: string, mode: SyncMode) => void;
}

interface CreateSyncTaskFormValues extends CreateSyncTaskValues {
  sourceDbType: string;
  targetDbType: string;
}

interface ConnectorOption {
  value: string;
  label: ReactNode;
  pluginName?: string;
}

const DEFAULT_DB_TYPE = 'MYSQL';

const brandCssVariables = {
  '--yak-brand-color': BRAND_COLOR,
  '--yak-brand-color-border': BRAND_COLOR_BORDER,
  '--yak-brand-color-soft': BRAND_COLOR_SOFT,
  '--yak-brand-color-soft-hover': BRAND_COLOR_SOFT_HOVER,
} as CSSProperties;

const resolveEndpoint = (
  dbType: string,
  options: ConnectorOption[],
): CreateSyncEndpoint => {
  const option = options.find((item) => item.value === dbType);

  return {
    dbType,
    connectorId: connectorIdForDataSourceType(dbType),
    pluginName: option?.pluginName || `JDBC-${dbType}`,
  };
};

export default function CreateSyncTaskDrawer({
  open,
  onCancel,
  onCreated,
}: CreateSyncTaskDrawerProps) {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [form] = Form.useForm<CreateSyncTaskFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [icon, setIcon] = useState<EmojiIconValue>(DEFAULT_EMOJI_ICON);
  const autoJobNameRef = useRef('');

  const connectorOptions = useMemo(
    () => generateDataSourceOptions() as ConnectorOption[],
    [],
  );

  const sourceDbType = Form.useWatch('sourceDbType', form);
  const targetDbType = Form.useWatch('targetDbType', form);
  const modeOptions: Array<{
    value: SyncMode;
    title: string;
    description: string;
    icon: ReactNode;
  }> = [
    {
      value: 'GUIDE_SINGLE',
      title: intl.formatMessage({ id: 'pages.batchLinkUp.create.mode.single' }),
      description: intl.formatMessage({
        id: 'pages.batchLinkUp.create.mode.singleDescription',
      }),
      icon: <TableOutlined />,
    },
    {
      value: 'GUIDE_MULTI',
      title: intl.formatMessage({ id: 'pages.batchLinkUp.create.mode.multi' }),
      description: intl.formatMessage({
        id: 'pages.batchLinkUp.create.mode.multiDescription',
      }),
      icon: <DatabaseOutlined />,
    },
  ];

  const createDefaultJobName = (source: string, target: string) =>
    intlRef.current
      .formatMessage(
        { id: 'pages.batchLinkUp.create.defaultJobName' },
        { source, target },
      )
      .slice(0, 64);

  useEffect(() => {
    if (!open) return;

    const defaultDbType =
      connectorOptions.find((item) => item.value === DEFAULT_DB_TYPE)?.value ||
      connectorOptions[0]?.value ||
      '';
    const defaultJobName = intlRef.current
      .formatMessage(
        { id: 'pages.batchLinkUp.create.defaultJobName' },
        { source: defaultDbType, target: defaultDbType },
      )
      .slice(0, 64);

    autoJobNameRef.current = defaultJobName;
    setIcon(DEFAULT_EMOJI_ICON);
    form.setFieldsValue({
      sourceDbType: defaultDbType,
      targetDbType: defaultDbType,
      jobName: defaultJobName,
      jobDesc: undefined,
      mode: 'GUIDE_SINGLE',
    });
  }, [connectorOptions, form, open]);

  const updateAutoJobName = (side: 'source' | 'target', value: string) => {
    const nextSourceDbType =
      side === 'source' ? value : form.getFieldValue('sourceDbType') || '';
    const nextTargetDbType =
      side === 'target' ? value : form.getFieldValue('targetDbType') || '';

    if (!nextSourceDbType || !nextTargetDbType) return;

    const currentJobName = form.getFieldValue('jobName')?.trim() || '';
    const nextJobName = createDefaultJobName(
      nextSourceDbType,
      nextTargetDbType,
    );

    if (!currentJobName || currentJobName === autoJobNameRef.current) {
      form.setFieldValue('jobName', nextJobName);
    }
    autoJobNameRef.current = nextJobName;
  };

  const handleCancel = () => {
    if (submitting) return;

    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
    autoJobNameRef.current = '';
    onCancel();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const normalizedValues: CreateSyncTaskValues = {
        jobName: values.jobName.trim(),
        jobDesc: values.jobDesc?.trim(),
        mode: values.mode,
      };
      const source = resolveEndpoint(values.sourceDbType, connectorOptions);
      const sink = resolveEndpoint(values.targetDbType, connectorOptions);

      setSubmitting(true);
      const taskId = String(await getOfflineSyncUniqueId());
      const payload = {
        ...buildCreatePayload(taskId, normalizedValues, source, sink),
        editorMeta: { icon },
      };
      const savedId = await createOfflineSyncDraft(payload);
      const createdId = String(savedId ?? taskId);
      const path =
        normalizedValues.mode === 'GUIDE_MULTI'
          ? `/sync/batch-link-up/${createdId}/config/multi?scene=edit`
          : `/sync/batch-link-up/${createdId}/config/single?scene=edit`;

      form.resetFields();
      setIcon(DEFAULT_EMOJI_ICON);
      autoJobNameRef.current = '';
      message.success(
        intl.formatMessage({ id: 'pages.batchLinkUp.create.success' }),
      );
      onCreated(createdId, normalizedValues.mode);
      history.push(path);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.batchLinkUp.create.failed' }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <Drawer
        open={open}
        width={620}
        placement="right"
        closable={false}
        destroyOnClose
        maskClosable={false}
        keyboard={!submitting}
        rootStyle={brandCssVariables}
        onClose={handleCancel}
        title={
          <div className="min-w-0">
            <div className="text-[18px] font-semibold leading-7 text-[#101828]">
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.title' })}
            </div>
          </div>
        }
        extra={
          <div className="flex shrink-0 items-center gap-2">
            <YakButton
              type="text"
              disabled={submitting}
              onClick={handleCancel}
              className="!h-9 !rounded-lg !px-4 !font-medium !text-[#667085]"
            >
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.cancel' })}
            </YakButton>

            <YakButton
              type="primary"
              loading={submitting}
              disabled={!sourceDbType || !targetDbType}
              onClick={handleSubmit}
              className="!h-9 !rounded-lg !px-5 !font-medium !text-white"
            >
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.submit' })}
            </YakButton>
          </div>
        }
        styles={{
          header: {
            padding: '18px 24px',
            borderBottom: '1px solid #eaecf0',
          },
          body: {
            padding: '24px',
          },
        }}
      >
        <Form<CreateSyncTaskFormValues>
          form={form}
          layout="vertical"
          requiredMark="optional"
        >
          <div className="mb-6">
            <div className="grid grid-cols-[minmax(0,1fr)_32px_minmax(0,1fr)] items-end gap-3">
              <Form.Item
                name="sourceDbType"
                label={intl.formatMessage({
                  id: 'pages.batchLinkUp.create.sourceType',
                })}
                className="!mb-0"
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.sourceTypeRequired',
                    }),
                  },
                ]}
              >
                <Select
                  showSearch
                  variant="filled"
                  options={connectorOptions}
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.sourceTypePlaceholder',
                  })}
                  optionFilterProp="value"
                  filterOption={(input, option) =>
                    String(option?.value || '')
                      .toLowerCase()
                      .includes(input.toLowerCase())
                  }
                  onChange={(value) => updateAutoJobName('source', value)}
                />
              </Form.Item>

              <div className="flex h-8 items-center justify-center text-[#98a2b3]">
                <ArrowRightOutlined />
              </div>

              <Form.Item
                name="targetDbType"
                label={intl.formatMessage({
                  id: 'pages.batchLinkUp.create.targetType',
                })}
                className="!mb-0"
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.targetTypeRequired',
                    }),
                  },
                ]}
              >
                <Select
                  showSearch
                  variant="filled"
                  options={connectorOptions}
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.targetTypePlaceholder',
                  })}
                  optionFilterProp="value"
                  filterOption={(input, option) =>
                    String(option?.value || '')
                      .toLowerCase()
                      .includes(input.toLowerCase())
                  }
                  onChange={(value) => updateAutoJobName('target', value)}
                />
              </Form.Item>
            </div>
          </div>

          <Form.Item
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.jobName' })}
            required
            className="!mb-6"
          >
            <div className="flex items-start gap-2.5">
              <EmojiIconPicker
                value={icon}
                disabled={submitting}
                onChange={setIcon}
                className="mt-px"
              />
              <Form.Item
                name="jobName"
                noStyle
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.jobNameRequired',
                    }),
                  },
                  {
                    max: 64,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.jobNameMax',
                    }),
                  },
                ]}
              >
                <Input
                  autoFocus
                  maxLength={64}
                  showCount
                  variant="filled"
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.jobNamePlaceholder',
                  })}
                  className="!h-[44px] !rounded-[10px]"
                />
              </Form.Item>
            </div>
          </Form.Item>

          <Form.Item
            name="jobDesc"
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.jobDesc' })}
            rules={[
              {
                max: 200,
                message: intl.formatMessage({
                  id: 'pages.batchLinkUp.create.jobDescMax',
                }),
              },
            ]}
          >
            <Input.TextArea
              rows={5}
              maxLength={200}
              variant="filled"
              showCount
              placeholder={intl.formatMessage({
                id: 'pages.batchLinkUp.create.jobDescPlaceholder',
              })}
            />
          </Form.Item>

          <Form.Item
            name="mode"
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.mode' })}
            rules={[
              {
                required: true,
                message: intl.formatMessage({
                  id: 'pages.batchLinkUp.create.modeRequired',
                }),
              },
            ]}
          >
            <Radio.Group className="grid w-full grid-cols-2 gap-2.5">
              {modeOptions.map((option) => (
                <Radio.Button
                  key={option.value}
                  value={option.value}
                  className={[
                    '!h-auto',
                    '!rounded-lg',
                    '!border',
                    '!border-[#e4e7ec]',
                    '!bg-white',
                    '!px-3',
                    '!py-3',
                    '!shadow-none',
                    'hover:!border-[var(--yak-brand-color-border)]',
                    'hover:!bg-[#fbfcfe]',
                    '[&.ant-radio-button-wrapper-checked]:!border-[var(--yak-brand-color)]',
                    '[&.ant-radio-button-wrapper-checked]:!bg-white',
                    '[&.ant-radio-button-wrapper-checked]:!text-inherit',
                    '[&.ant-radio-button-wrapper-checked]:!shadow-[0_0_0_2px_rgba(201,40,72,0.08)]',
                    'before:!hidden',
                  ].join(' ')}
                >
                  <div className="flex items-start gap-2.5 whitespace-normal">
                    <div
                      className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[15px]"
                      style={{
                        color: BRAND_COLOR,
                        backgroundColor: BRAND_COLOR_SOFT_HOVER,
                      }}
                    >
                      {option.icon}
                    </div>

                    <div className="min-w-0 text-left">
                      <div className="text-[13px] font-medium leading-5 text-[#182230]">
                        {option.title}
                      </div>

                      <div className="mt-0.5 text-[11px] leading-[18px] text-[#667085]">
                        {option.description}
                      </div>
                    </div>
                  </div>
                </Radio.Button>
              ))}
            </Radio.Group>
          </Form.Item>
        </Form>
      </Drawer>
    </ConfigProvider>
  );
}
