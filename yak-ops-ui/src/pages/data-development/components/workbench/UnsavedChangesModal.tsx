import { useIntl } from '@umijs/max';
import { Button, Modal } from 'antd';
import { TriangleAlert } from 'lucide-react';

interface UnsavedChangesModalProps {
  open: boolean;
  saving: boolean;
  dirtyNames: string[];
  onSave: () => void | Promise<void>;
  onDiscard: () => void | Promise<void>;
  onCancel: () => void;
}

const UnsavedChangesModal = ({
  open,
  saving,
  dirtyNames,
  onSave,
  onDiscard,
  onCancel,
}: UnsavedChangesModalProps) => {
  const intl = useIntl();
  const dirtyCount = dirtyNames.length;
  const dirtyName = dirtyNames[0] || intl.formatMessage({ id: 'pages.dataDevelopment.unsaved.current' });

  return (
    <Modal
      open={open}
      title={null}
      footer={null}
      width={520}
      centered
      maskClosable={false}
      closable={!saving}
      onCancel={() => {
        if (!saving) onCancel();
      }}
    >
      <div className="flex gap-4 px-1 py-2">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center text-[#f79009]">
          <TriangleAlert size={36} strokeWidth={1.7} />
        </div>

        <div className="min-w-0 flex-1 pt-1">
          <div className="text-[16px] font-medium leading-6 text-[#1f2937]">
            {intl.formatMessage(
              {
                id:
                  dirtyCount <= 1
                    ? 'pages.dataDevelopment.unsaved.singleTitle'
                    : 'pages.dataDevelopment.unsaved.multipleTitle',
              },
              dirtyCount <= 1 ? { name: dirtyName } : { count: dirtyCount },
            )}
          </div>

          <div className="mt-4 text-[13px] leading-5 text-[#475467]">
            {intl.formatMessage({
              id:
                dirtyCount <= 1
                  ? 'pages.dataDevelopment.unsaved.singleHint'
                  : 'pages.dataDevelopment.unsaved.multipleHint',
            })}
          </div>

          {dirtyCount > 1 ? (
            <div
              className="mt-2 max-w-[360px] truncate text-[12px] text-[#98a2b3]"
              title={dirtyNames.join(' · ')}
            >
              {dirtyNames.join(' · ')}
            </div>
          ) : null}

          <div className="mt-6 flex justify-end gap-2">
            <Button type="primary" loading={saving} onClick={() => void onSave()}>
              {intl.formatMessage({
                id:
                  dirtyCount > 1
                    ? 'pages.dataDevelopment.unsaved.saveAll'
                    : 'pages.dataDevelopment.unsaved.save',
              })}
            </Button>
            <Button disabled={saving} onClick={() => void onDiscard()}>
              {intl.formatMessage({
                id:
                  dirtyCount > 1
                    ? 'pages.dataDevelopment.unsaved.discardAll'
                    : 'pages.dataDevelopment.unsaved.discard',
              })}
            </Button>
            <Button disabled={saving} onClick={onCancel}>
              {intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default UnsavedChangesModal;
