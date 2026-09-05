import {
  createWorkflowBackfill,
  previewWorkflowBackfill,
  type WorkflowBackfillPayload,
  type WorkflowBackfillPreview,
  type WorkflowSchedule,
} from '@/services/workflow/schedules';
import { useIntl } from '@umijs/max';
import { Button, DatePicker, Drawer, Form, Input, Select, Table, message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { DatabaseBackup, Eye } from 'lucide-react';
import { useEffect, useState } from 'react';

interface BackfillFormValues {
  name?: string;
  businessDateRange: [Dayjs, Dayjs];
  executionStrategy: 'SERIAL_WAIT' | 'PARALLEL';
  inputJson: string;
}

interface BackfillDrawerProps {
  open: boolean;
  schedule?: WorkflowSchedule;
  onClose: () => void;
  onCreated: () => Promise<void> | void;
}

const BackfillDrawer = ({ open, schedule, onClose, onCreated }: BackfillDrawerProps) => {
  const intl = useIntl();
  const [form] = Form.useForm<BackfillFormValues>();
  const [preview, setPreview] = useState<WorkflowBackfillPreview>();
  const [previewing, setPreviewing] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!open) return;
    setPreview(undefined);
    form.setFieldsValue({
      name: '',
      executionStrategy: 'SERIAL_WAIT',
      inputJson: '{}',
      businessDateRange: [dayjs().subtract(1, 'day'), dayjs().subtract(1, 'day')],
    });
  }, [form, open, schedule?.id]);

  const buildPayload = async (): Promise<WorkflowBackfillPayload | undefined> => {
    if (!schedule) return undefined;
    const values = await form.validateFields();
    let input: Record<string, unknown> = {};
    try {
      input = values.inputJson?.trim() ? JSON.parse(values.inputJson) : {};
    } catch {
      message.error(intl.formatMessage({ id: 'pages.workflow.schedule.backfill.jsonInvalid' }));
      return undefined;
    }
    return {
      scheduleId: schedule.id,
      name: values.name?.trim() || undefined,
      startBusinessDate: values.businessDateRange[0].format('YYYY-MM-DD'),
      endBusinessDate: values.businessDateRange[1].format('YYYY-MM-DD'),
      executionStrategy: values.executionStrategy,
      input,
    };
  };

  const handlePreview = async () => {
    try {
      const payload = await buildPayload();
      if (!payload) return;
      setPreviewing(true);
      setPreview(await previewWorkflowBackfill(payload));
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.workflow.schedule.backfill.previewFailed' }),
      );
    } finally {
      setPreviewing(false);
    }
  };

  const handleCreate = async () => {
    try {
      const payload = await buildPayload();
      if (!payload) return;
      setCreating(true);
      const result = await createWorkflowBackfill(payload);
      message.success(
        intl.formatMessage(
          { id: 'pages.workflow.schedule.backfill.created' },
          { count: result?.totalCount ?? preview?.totalCount ?? 0 },
        ),
      );
      await onCreated();
      onClose();
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.workflow.schedule.backfill.createFailed' }),
      );
    } finally {
      setCreating(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={680}
      destroyOnClose
      title={
        <div>
          <div className="text-[14px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.workflow.schedule.backfill.title' })}
          </div>
          <div className="mt-0.5 text-[11px] font-normal text-[#98a2b3]">
            {schedule?.name || '-'} · {schedule?.cronExpression || '-'} · {schedule?.timezone || '-'}
          </div>
        </div>
      }
      onClose={onClose}
      extra={
        <div className="flex gap-2">
          <Button icon={<Eye size={14} />} loading={previewing} onClick={() => void handlePreview()}>
            {intl.formatMessage({ id: 'pages.workflow.schedule.backfill.preview' })}
          </Button>
          <Button type="primary" icon={<DatabaseBackup size={14} />} loading={creating} onClick={() => void handleCreate()}>
            {intl.formatMessage({ id: 'pages.workflow.schedule.backfill.create' })}
          </Button>
        </div>
      }
    >
      <div className="mb-4 rounded-md border border-[#eaecf0] bg-[#f8f9fb] px-3 py-2 text-[11px] leading-5 text-[#667085]">
        {intl.formatMessage({ id: 'pages.workflow.schedule.backfill.description' })}
      </div>

      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          name="name"
          label={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.name' })}
          rules={[{ max: 120 }]}
        >
          <Input
            variant="filled"
            placeholder={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.namePlaceholder' })}
          />
        </Form.Item>

        <Form.Item
          name="businessDateRange"
          label={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.dateRange' })}
          rules={[
            {
              required: true,
              message: intl.formatMessage({ id: 'pages.workflow.schedule.backfill.dateRangeRequired' }),
            },
          ]}
          extra={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.dateRangeHelp' })}
        >
          <DatePicker.RangePicker className="w-full" allowClear={false} />
        </Form.Item>

        <Form.Item
          name="executionStrategy"
          label={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.strategy' })}
          rules={[{ required: true }]}
        >
          <Select
            options={[
              {
                value: 'SERIAL_WAIT',
                label: intl.formatMessage({ id: 'pages.workflow.schedule.backfill.serial' }),
              },
              {
                value: 'PARALLEL',
                label: intl.formatMessage({ id: 'pages.workflow.schedule.backfill.parallel' }),
              },
            ]}
          />
        </Form.Item>

        <Form.Item
          name="inputJson"
          label={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.input' })}
          extra={intl.formatMessage({ id: 'pages.workflow.schedule.backfill.inputHelp' })}
        >
          <Input.TextArea rows={6} spellCheck={false} className="font-mono text-[12px]" />
        </Form.Item>
      </Form>

      {preview ? (
        <div className="mt-2">
          <div className="mb-2 flex items-center justify-between">
            <div className="text-[12px] font-medium text-[#344054]">
              {intl.formatMessage({ id: 'pages.workflow.schedule.backfill.planPreview' })}
            </div>
            <div className="text-[11px] text-[#667085]">
              {intl.formatMessage(
                { id: 'pages.workflow.schedule.backfill.planCount' },
                { count: preview.totalCount },
              )}
              {preview.truncated
                ? intl.formatMessage({ id: 'pages.workflow.schedule.backfill.truncated' })
                : ''}
            </div>
          </div>
          <Table
            rowKey={(record) => `${record.businessDate}-${record.scheduleInstant}`}
            size="small"
            bordered
            pagination={false}
            scroll={{ y: 280 }}
            dataSource={preview.occurrences}
            columns={[
              {
                title: 'businessDate',
                dataIndex: 'businessDate',
                width: 130,
                render: (value: string) => <code className="text-[12px] text-[#344054]">{value}</code>,
              },
              {
                title: 'scheduleTime',
                dataIndex: 'scheduleTime',
                render: (value: string) => <code className="text-[11px] text-[#667085]">{value}</code>,
              },
            ]}
            className="[&_.ant-table-thead>tr>th]:!bg-[#f8f9fb] [&_.ant-table-thead>tr>th]:!text-[11px]"
          />
        </div>
      ) : null}
    </Drawer>
  );
};

export default BackfillDrawer;
