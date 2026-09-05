import { YakButton } from '@/components/ui';
import {
  createRealtimeSyncBasicTask,
  listRealtimeComputeEnvironments,
  type ComputeEnvironmentOption,
} from '@/services/realtime-sync';
import { CodeOutlined, CompassOutlined } from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import { Drawer, Form, Input, message, Select, Spin } from 'antd';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import type { RealtimeEditorMode } from '../realtimeEditorMode';
import { preferredRealtimeEnvironmentId } from '../utils';

interface CreateRealtimeTaskValues {
  name: string;
  description?: string;
  runtimeEnvironmentId: number;
}

interface CreateRealtimeTaskDrawerProps {
  open: boolean;
  onClose: () => void;
}

const CreateRealtimeTaskDrawer = ({ open, onClose }: CreateRealtimeTaskDrawerProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [form] = Form.useForm<CreateRealtimeTaskValues>();
  const [editorMode, setEditorMode] = useState<RealtimeEditorMode>('wizard');
  const [submitting, setSubmitting] = useState(false);
  const [environmentLoading, setEnvironmentLoading] = useState(false);
  const [environments, setEnvironments] = useState<ComputeEnvironmentOption[]>([]);

  const editorModes: Array<{
    value: RealtimeEditorMode;
    title: string;
    description: string;
    badge?: string;
    icon: ReactNode;
  }> = [
    {
      value: 'wizard',
      title: intl.formatMessage({ id: 'pages.realtimeSync.create.wizard' }),
      description: intl.formatMessage({ id: 'pages.realtimeSync.create.wizardDescription' }),
      badge: intl.formatMessage({ id: 'pages.realtimeSync.create.recommended' }),
      icon: <CompassOutlined />,
    },
    {
      value: 'yaml',
      title: intl.formatMessage({ id: 'pages.realtimeSync.create.yaml' }),
      description: intl.formatMessage({ id: 'pages.realtimeSync.create.yamlDescription' }),
      icon: <CodeOutlined />,
    },
  ];

  const environmentOptions = useMemo(
    () =>
      environments.map((environment) => ({
        value: environment.id,
        disabled: !environment.enabled,
        label: `${environment.name}${
          environment.defaultEnvironment
            ? ` · ${intl.formatMessage({ id: 'pages.realtimeSync.create.environmentDefault' })}`
            : ''
        } · Flink ${environment.config.flinkVersion} / CDC ${environment.config.flinkCdcVersion}${
          environment.enabled
            ? ''
            : ` · ${intl.formatMessage({ id: 'pages.realtimeSync.create.environmentDisabled' })}`
        }`,
      })),
    [environments, intl],
  );

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setEditorMode('wizard');

    let active = true;
    const loadEnvironments = async () => {
      setEnvironmentLoading(true);
      try {
        const rows = await listRealtimeComputeEnvironments();
        if (!active) return;
        setEnvironments(rows || []);
        const defaultId = preferredRealtimeEnvironmentId(rows || []);
        if (defaultId) form.setFieldValue('runtimeEnvironmentId', defaultId);
      } catch (error) {
        if (active) {
          setEnvironments([]);
          message.error(
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({
                  id: 'pages.realtimeSync.create.environmentLoadFailed',
                }),
          );
        }
      } finally {
        if (active) setEnvironmentLoading(false);
      }
    };

    void loadEnvironments();
    return () => {
      active = false;
    };
  }, [form, open]);

  const handleClose = () => {
    if (submitting) return;
    onClose();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const environment = environments.find(
        (item) => item.id === Number(values.runtimeEnvironmentId),
      );
      if (!environment || !environment.enabled) {
        message.error(
          intl.formatMessage({ id: 'pages.realtimeSync.create.enabledEnvironmentRequired' }),
        );
        return;
      }

      setSubmitting(true);
      const taskId = await createRealtimeSyncBasicTask({
        name: values.name.trim(),
        description: values.description?.trim(),
        runtimeEnvironmentId: Number(values.runtimeEnvironmentId),
      });

      message.success(intl.formatMessage({ id: 'pages.realtimeSync.create.success' }));
      form.resetFields();
      history.push(`/sync/realtime/${taskId}/detail?scene=create&editor=${editorMode}`);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.realtimeSync.create.failed' }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={intl.formatMessage({ id: 'pages.realtimeSync.create.title' })}
      width={680}
      open={open}
      closable={false}
      maskClosable={false}
      keyboard={!submitting}
      onClose={handleClose}
      extra={
        <div className="flex gap-2">
          <YakButton disabled={submitting} onClick={handleClose}>
            {intl.formatMessage({ id: 'pages.realtimeSync.create.cancel' })}
          </YakButton>
          <YakButton
            type="primary"
            danger
            loading={submitting}
            onClick={() => void handleSubmit()}
          >
            {intl.formatMessage({ id: 'pages.realtimeSync.create.submit' })}
          </YakButton>
        </div>
      }
      styles={{ header: { padding: '18px 24px' }, body: { padding: 24 } }}
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <div className="mb-2 text-[14px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.realtimeSync.create.method' })}
        </div>
        <div className="mb-6 grid grid-cols-2 gap-3 max-sm:grid-cols-1">
          {editorModes.map((mode) => {
            const selected = editorMode === mode.value;
            return (
              <button
                key={mode.value}
                type="button"
                className={[
                  'relative min-h-[142px] rounded-xl border bg-white p-5 text-left transition-all',
                  selected
                    ? 'border-[#ff4d4f] shadow-[0_0_0_2px_rgba(255,77,79,0.08)]'
                    : 'border-[#e4e7ec] hover:border-[#fda29b] hover:bg-[#fffbfa]',
                ].join(' ')}
                onClick={() => setEditorMode(mode.value)}
              >
                <div className="flex items-start justify-between gap-3">
                  <span
                    className={[
                      'flex h-9 w-9 items-center justify-center rounded-lg text-[18px]',
                      selected
                        ? 'bg-[#fff1f0] text-[#ff4d4f]'
                        : 'bg-[#f2f4f7] text-[#667085]',
                    ].join(' ')}
                  >
                    {mode.icon}
                  </span>
                  {mode.badge ? (
                    <span className="rounded-full bg-[#fff1f0] px-2 py-0.5 text-[11px] font-medium text-[#ff4d4f]">
                      {mode.badge}
                    </span>
                  ) : null}
                </div>
                <div className="mt-4 text-[15px] font-semibold text-[#101828]">
                  {mode.title}
                </div>
                <div className="mt-1.5 text-[12px] leading-5 text-[#667085]">
                  {mode.description}
                </div>
              </button>
            );
          })}
        </div>

        <Form.Item
          name="runtimeEnvironmentId"
          label={intl.formatMessage({ id: 'pages.realtimeSync.create.environment' })}
          rules={[
            {
              required: true,
              message: intl.formatMessage({ id: 'pages.realtimeSync.create.environmentRequired' }),
            },
          ]}
          extra={intl.formatMessage({ id: 'pages.realtimeSync.create.environmentExtra' })}
        >
          <Select
            showSearch
            optionFilterProp="label"
            variant="filled"
            loading={environmentLoading}
            disabled={environmentLoading}
            placeholder={intl.formatMessage({
              id: 'pages.realtimeSync.create.environmentPlaceholder',
            })}
            options={environmentOptions}
            notFoundContent={
              environmentLoading ? (
                <Spin size="small" />
              ) : (
                intl.formatMessage({ id: 'pages.realtimeSync.create.environmentNotFound' })
              )
            }
          />
        </Form.Item>

        <Form.Item
          name="name"
          label={intl.formatMessage({ id: 'pages.realtimeSync.create.name' })}
          rules={[
            {
              required: true,
              message: intl.formatMessage({ id: 'pages.realtimeSync.create.nameRequired' }),
            },
            {
              max: 200,
              message: intl.formatMessage({ id: 'pages.realtimeSync.create.nameMax' }),
            },
          ]}
        >
          <Input
            autoFocus
            variant="filled"
            maxLength={200}
            showCount
            placeholder={intl.formatMessage({ id: 'pages.realtimeSync.create.namePlaceholder' })}
          />
        </Form.Item>

        <Form.Item
          name="description"
          label={intl.formatMessage({ id: 'pages.realtimeSync.create.description' })}
          rules={[
            {
              max: 1000,
              message: intl.formatMessage({ id: 'pages.realtimeSync.create.descriptionMax' }),
            },
          ]}
        >
          <Input.TextArea
            variant="filled"
            rows={4}
            maxLength={1000}
            showCount
            placeholder={intl.formatMessage({
              id: 'pages.realtimeSync.create.descriptionPlaceholder',
            })}
          />
        </Form.Item>

        <div className="rounded-lg bg-[#f9fafb] p-4 text-sm leading-6 text-[#667085]">
          {intl.formatMessage({
            id:
              editorMode === 'wizard'
                ? 'pages.realtimeSync.create.wizardHint'
                : 'pages.realtimeSync.create.yamlHint',
          })}
        </div>
      </Form>
    </Drawer>
  );
};

export default CreateRealtimeTaskDrawer;
