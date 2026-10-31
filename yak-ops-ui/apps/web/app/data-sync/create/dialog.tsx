import { Alert, Button, Field, FieldLabel, Input, Modal } from "@yak-ops/yak-ui";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { COMMON_DB_OPTIONS } from "@/app/datasource/constants";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import type { DataSyncDraft, DataSyncStep } from "@/service/data-sync";
import { readTaskDraft, taskDraftKey, writeTaskDraft } from "@/service/data-sync/draft";

import { DataSyncSearchableSelect } from "../components/searchable-select";
import { findStepCombination } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";

export function CreateSyncTaskDialog({ onClose }: { onClose: () => void }) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const key = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const stored = useRef(readTaskDraft(key));
  const [sourceType, setSourceType] = useState(stored.current?.sourceType || "");
  const [targetType, setTargetType] = useState(stored.current?.targetType || "");
  const [connectorChoice, setConnectorChoice] = useState<string | undefined>(
    stored.current
      ? `${stored.current.sourceConnector}:${stored.current.sinkConnector}:${stored.current.productType}`
      : undefined,
  );
  const [steps, setSteps] = useState<DataSyncStep[]>(stored.current?.steps || []);
  const [customName, setCustomName] = useState<string | undefined>(stored.current?.name);
  const timestamp = useRef(
    new Date()
      .toISOString()
      .replace(/[-:TZ.]/g, "")
      .slice(0, 14),
  );
  const [pending, setPending] = useState<DataSyncDraft>();
  const navigate = useNavigate();
  const { choices, loading, error, refresh } = useSolutions(sourceType, targetType);
  const selected = choices.find((choice) => choice.id === connectorChoice);
  const combination = findStepCombination(selected, steps);

  const name =
    customName ??
    (sourceType && targetType
      ? `sync_${sourceType.toLowerCase()}_to_${targetType.toLowerCase()}_${timestamp.current}`
      : "");

  const changeType = (side: "source" | "target", value: string) => {
    if (side === "source") setSourceType(value);
    else setTargetType(value);
    setConnectorChoice(undefined);
    setSteps([]);
  };
  const openEditor = (draft: DataSyncDraft) => {
    writeTaskDraft(key, draft);
    onClose();
    navigate("/data-sync/new", { state: { draftKey: key, draft } });
  };
  const next = () => {
    if (!selected || !combination || loading || error || !name.trim()) return;
    const draft: DataSyncDraft = {
      sourceType,
      targetType,
      sourceConnector: selected.sourceConnector,
      sinkConnector: selected.sinkConnector,
      sourceProducesChangelog: selected.producesChangelog,
      productType: selected.productType,
      steps: [...combination.steps],
      name: name.trim(),
    };
    const previous = stored.current;
    if (previous?.form) {
      if (
        previous.sourceType !== sourceType ||
        previous.targetType !== targetType ||
        previous.productType !== selected.productType ||
        previous.sourceConnector !== selected.sourceConnector ||
        previous.sinkConnector !== selected.sinkConnector ||
        !findStepCombination(selected, previous.steps)
      ) {
        setPending(draft);
        return;
      }
      draft.steps = [...previous.steps];
      draft.form = { ...previous.form, name: draft.name, steps: draft.steps };
    }
    openEditor(draft);
  };

  return (
    <>
      <Modal
        open
        centered
        width={560}
        title="新建同步任务"
        onClose={onClose}
        footer={
          <>
            <Button size="small" onClick={onClose}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={!name.trim() || !combination || loading || Boolean(error)}
              onClick={next}
            >
              下一步
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>数据来源类型</FieldLabel>
            <DataSyncSearchableSelect
              value={sourceType || null}
              options={COMMON_DB_OPTIONS}
              placeholder="请选择来源类型"
              onValueChange={(value) => changeType("source", value)}
            />
          </Field>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>数据去向类型</FieldLabel>
            <DataSyncSearchableSelect
              value={targetType || null}
              options={COMMON_DB_OPTIONS}
              placeholder="请选择去向类型"
              onValueChange={(value) => changeType("target", value)}
            />
          </Field>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>具体类型</FieldLabel>
            <DataSyncSearchableSelect
              value={selected?.id || null}
              options={choices.map((choice) => ({
                value: choice.id,
                label: choice.displayName,
              }))}
              disabled={!sourceType || !targetType || loading || Boolean(error)}
              placeholder={loading ? "加载同步方案…" : "请选择具体类型"}
              onValueChange={(value) => {
                const choice = choices.find((item) => item.id === value);
                if (choice) {
                  setConnectorChoice(choice.id);
                  setSteps([...choice.defaultSteps]);
                }
              }}
            />
          </Field>
          {error ? (
            <Alert>
              <span>{error}</span>
              <Button size="small" variant="ghost" onClick={refresh}>
                重试
              </Button>
            </Alert>
          ) : null}
          {!error && !loading && sourceType && targetType && choices.length === 0 ? (
            <Alert>当前链路暂无可用同步方案</Alert>
          ) : null}
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>名称</FieldLabel>
            <Input
              size="small"
              variant="outlined"
              maxLength={128}
              value={name}
              placeholder="同步任务名称"
              onChange={(event) => {
                setCustomName(event.target.value);
              }}
            />
          </Field>
        </div>
      </Modal>
      {pending ? (
        <Modal
          open
          centered
          width={420}
          title="更改同步方案"
          onClose={() => setPending(undefined)}
          footer={
            <>
              <Button size="small" onClick={() => setPending(undefined)}>
                取消
              </Button>
              <Button size="small" variant="primary" onClick={() => openEditor(pending)}>
                继续
              </Button>
            </>
          }
        >
          <p className="text-sm text-[var(--yak-components-collapse-section-icon)]">
            更改具体类型将清除草稿中的连接、表映射、字段映射及转换表达式。
          </p>
        </Modal>
      ) : null}
    </>
  );
}
