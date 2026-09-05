import type { DashboardSummary } from '@/services/dashboard';
import { useIntl } from '@umijs/max';
import { Input, Modal } from 'antd';

interface RenameDashboardModalProps {
  dashboard?: DashboardSummary;
  value: string;
  loading: boolean;
  onChange: (value: string) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

const RenameDashboardModal = ({
  dashboard,
  value,
  loading,
  onChange,
  onCancel,
  onSubmit,
}: RenameDashboardModalProps) => {
  const intl = useIntl();

  return (
    <Modal
      title={intl.formatMessage({ id: 'pages.dashboard.list.rename.title' })}
      open={Boolean(dashboard)}
      okText={intl.formatMessage({ id: 'pages.dashboard.list.rename.saveDraft' })}
      cancelText={intl.formatMessage({ id: 'pages.dashboard.common.cancel' })}
      confirmLoading={loading}
      maskClosable={!loading}
      closable={!loading}
      onOk={onSubmit}
      onCancel={onCancel}
    >
      <Input
        autoFocus
        maxLength={128}
        value={value}
        placeholder={intl.formatMessage({ id: 'pages.dashboard.list.rename.placeholder' })}
        disabled={loading}
        onChange={(event) => onChange(event.target.value)}
        onPressEnter={onSubmit}
      />

      {dashboard?.publishedVersionNo ? (
        <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dashboard.list.rename.publishedHint' })}
        </div>
      ) : null}
    </Modal>
  );
};

export default RenameDashboardModal;
