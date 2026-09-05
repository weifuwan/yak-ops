import EmojiIconPicker, {
  DEFAULT_EMOJI_ICON,
  type EmojiIconValue,
} from '@/components/EmojiIconPicker';
import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Drawer, Form, Input } from 'antd';
import { useEffect, useState } from 'react';

export interface WorkflowCreateValues {
  name: string;
  description?: string;
  icon: EmojiIconValue;
}

interface WorkflowCreateDrawerProps {
  open: boolean;
  creating: boolean;
  onClose: () => void;
  onSubmit: (values: WorkflowCreateValues) => Promise<void>;
}

const WorkflowCreateDrawer = ({
  open,
  creating,
  onClose,
  onSubmit,
}: WorkflowCreateDrawerProps) => {
  const intl = useIntl();
  const [form] = Form.useForm<Omit<WorkflowCreateValues, 'icon'>>();
  const [icon, setIcon] = useState<EmojiIconValue>(DEFAULT_EMOJI_ICON);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
  }, [form, open]);

  const handleClose = () => {
    if (creating) return;
    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
    onClose();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await onSubmit({ ...values, icon });
      form.resetFields();
      setIcon(DEFAULT_EMOJI_ICON);
    } catch {
      // Form owns validation feedback; request failures are surfaced by the page action.
    }
  };

  return (
    <Drawer
      open={open}
      width={560}
      placement="right"
      closable={false}
      destroyOnClose
      maskClosable={!creating}
      keyboard={!creating}
      onClose={handleClose}
      title={
        <div className="text-[18px] font-semibold leading-7 text-[#101828]">
          {intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.title' })}
        </div>
      }
      extra={
        <div className="flex items-center gap-2">
          <YakButton
            disabled={creating}
            onClick={handleClose}
            className="!h-9 !rounded-lg !px-4"
          >
            {intl.formatMessage({ id: 'pages.workflow.common.cancel' })}
          </YakButton>
          <YakButton
            type="primary"
            loading={creating}
            onClick={() => void handleSubmit()}
            className="!h-9 !rounded-lg !px-5 !text-white"
          >
            {intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.create' })}
          </YakButton>
        </div>
      }
      styles={{
        header: {
          padding: '18px 24px',
          borderBottom: '1px solid #eaecf0',
        },
        body: { padding: 24 },
      }}
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item
          label={intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.name' })}
          required
          className="!mb-6"
        >
          <div className="flex items-start gap-2.5">
            <EmojiIconPicker
              value={icon}
              disabled={creating}
              onChange={setIcon}
              className="mt-px"
            />
            <Form.Item
              name="name"
              noStyle
              rules={[
                {
                  required: true,
                  message: intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.nameRequired' }),
                },
                {
                  max: 100,
                  message: intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.nameMax' }),
                },
              ]}
            >
              <Input
                variant="filled"
                placeholder={intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.namePlaceholder' })}
                className="!h-[44px] !rounded-[10px]"
              />
            </Form.Item>
          </div>
        </Form.Item>

        <Form.Item
          name="description"
          label={intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.description' })}
          rules={[
            {
              max: 500,
              message: intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.descriptionMax' }),
            },
          ]}
        >
          <Input.TextArea
            variant="filled"
            rows={4}
            placeholder={intl.formatMessage({ id: 'pages.workflow.definition.createDrawer.descriptionPlaceholder' })}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default WorkflowCreateDrawer;
