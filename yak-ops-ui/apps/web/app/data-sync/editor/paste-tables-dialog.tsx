import { useEffect, useRef, useState } from "react";
import { Alert, Button, Modal, Textarea } from "@yak-ops/yak-ui";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { parsePastedTableNames } from "./table-selection";

export function PasteTablesDialog({
  title,
  onSubmit,
  onClose,
}: {
  title: string;
  onSubmit: (names: string[], signal: AbortSignal) => Promise<void> | void;
  onClose: () => void;
}) {
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const controller = useRef<AbortController>();
  useEffect(() => {
    const request = new AbortController();
    controller.current = request;
    return () => request.abort();
  }, []);
  useUnsavedChanges(Boolean(text.trim()));
  const submit = async () => {
    const names = parsePastedTableNames(text);
    if (!names.length) {
      setError("请输入库表名称");
      return;
    }
    setLoading(true);
    setError("");
    const signal = controller.current!.signal;
    try {
      await onSubmit(names, signal);
      if (!signal.aborted) onClose();
    } catch (failure) {
      if (!signal.aborted) setError(failure instanceof Error ? failure.message : "选择库表失败");
    } finally {
      if (!signal.aborted) setLoading(false);
    }
  };
  return (
    <Modal
      open
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" loading={loading} onClick={() => void submit()}>
            确认
          </Button>
          <Button size="small" disabled={loading} onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <Textarea
        variant="outlined"
        aria-label="批量库表名称"
        className="min-h-48 font-mono text-xs"
        placeholder="每行一个库表名称，例如 app.orders"
        value={text}
        disabled={loading}
        onValueChange={(value) => setText(String(value))}
      />
      {error ? <Alert className="mt-3">{error}</Alert> : null}
    </Modal>
  );
}
