import { useIntl } from '@umijs/max';
import { Modal } from 'antd';

import type { DevelopmentTreeNode } from '../types';

interface DeleteDevelopmentResourceModalProps {
  target?: DevelopmentTreeNode;
  loading: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

const DeleteDevelopmentResourceModal = ({
  target,
  loading,
  onCancel,
  onConfirm,
}: DeleteDevelopmentResourceModalProps) => {
  const intl = useIntl();
  const resource = intl.formatMessage({
    id:
      target?.nodeType === 'directory'
        ? 'pages.dataDevelopment.common.directory'
        : 'pages.dataDevelopment.common.node',
  });

  return (
    <Modal
      open={Boolean(target)}
      title={intl.formatMessage(
        { id: 'pages.dataDevelopment.modal.delete.title' },
        { resource },
      )}
      okText={intl.formatMessage({ id: 'pages.dataDevelopment.common.delete' })}
      cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
      okButtonProps={{ danger: true }}
      confirmLoading={loading}
      maskClosable={!loading}
      closable={!loading}
      onCancel={onCancel}
      onOk={onConfirm}
    >
      <div className="pt-2 text-[13px] leading-6 text-[#475467]">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.modal.delete.confirm' },
          { name: target?.title || '' },
        )}
        {target?.nodeType === 'directory' ? (
          <div className="mt-1 text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.modal.delete.directoryHint' })}
          </div>
        ) : null}
      </div>
    </Modal>
  );
};

export default DeleteDevelopmentResourceModal;
