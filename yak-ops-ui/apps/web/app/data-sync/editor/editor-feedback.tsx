import { Alert, Button, Modal } from "@yak-ops/yak-ui";
import type { EditorConfirmation } from "./use-definition-restoration";

export function EditorConfirmationDialog({
  value,
  onClose,
}: {
  value: EditorConfirmation;
  onClose: () => void;
}) {
  return (
    <Modal
      open
      centered
      width={440}
      title={value.title}
      onClose={onClose}
      footer={
        <>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
          <Button
            size="small"
            variant="primary"
            onClick={() => {
              value.apply();
              onClose();
            }}
          >
            确认
          </Button>
        </>
      }
    >
      <p className="text-sm">{value.message}</p>
    </Modal>
  );
}

export function EditorRecoveryNotice({
  error,
  saving,
  canReconcile,
  canRetry,
  hasTask,
  onReconcile,
  onRetry,
  onReload,
  onBack,
}: {
  error: string;
  saving: boolean;
  canReconcile: boolean;
  canRetry: boolean;
  hasTask: boolean;
  onReconcile: () => void;
  onRetry: () => void;
  onReload: () => void;
  onBack: () => void;
}) {
  return (
    <Alert>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>{error}</span>
        <div className="flex gap-2">
          {canReconcile ? (
            <Button size="small" loading={saving} onClick={onReconcile}>
              重新加载状态
            </Button>
          ) : null}
          {canRetry ? (
            <Button size="small" loading={saving} onClick={onRetry}>
              重试本次发布
            </Button>
          ) : null}
          {hasTask ? (
            <Button size="small" disabled={saving} onClick={onReload}>
              加载远端配置
            </Button>
          ) : (
            <Button size="small" onClick={onBack}>
              返回列表核对
            </Button>
          )}
        </div>
      </div>
    </Alert>
  );
}
