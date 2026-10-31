import { Alert, Button, Checkbox, Field, FieldLabel, Input, Modal } from "@yak-ops/yak-ui";
import { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { COMMON_DB_OPTIONS } from "@/app/datasource/constants";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import type { DataSyncDraft, DataSyncStep } from "@/service/data-sync";
import { readTaskDraft, taskDraftKey, writeTaskDraft } from "@/service/data-sync/draft";

import { DataSyncSearchableSelect } from "../components/searchable-select";
import { findStepCombination, STEP_LABELS } from "../solution/presentation";
import { useSolutions } from "../solution/use-solutions";

export function CreateSyncTaskDialog({ onClose }: { onClose: () => void }) {
  const stepLabelId = useId();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const key = taskDraftKey(currentUser!.id, currentWorkspace!.id);
  const stored = useRef(readTaskDraft(key));
  const [sourceType, setSourceType] = useState(stored.current?.sourceType || "");
  const [targetType, setTargetType] = useState(stored.current?.targetType || "");
  const [connectorChoice, setConnectorChoice] = useState<string | undefined>(
    stored.current
      ? `${stored.current.sourceConnector}:${stored.current.sinkConnector}:${stored.current.solutionType}`
      : undefined,
  );
  const [steps, setSteps] = useState<DataSyncStep[]>(stored.current?.steps || []);
  const [name, setName] = useState(stored.current?.name || "");
  const nameEdited = useRef(Boolean(stored.current?.name));
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

  useEffect(() => {
    if (!nameEdited.current && sourceType && targetType) {
      setName(
        `sync_${sourceType.toLowerCase()}_to_${targetType.toLowerCase()}_${timestamp.current}`,
      );
    }
  }, [sourceType, targetType]);

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
      solutionType: selected.solutionType,
      steps: [...combination.steps],
      name: name.trim(),
    };
    const previous = stored.current;
    if (previous?.form) {
      if (
        previous.sourceType !== sourceType ||
        previous.targetType !== targetType ||
        previous.solutionType !== selected.solutionType ||
        previous.sourceConnector !== selected.sourceConnector ||
        previous.sinkConnector !== selected.sinkConnector ||
        !findStepCombination(selected, previous.steps)
      ) {
        setPending(draft);
        return;
      }
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
          {selected && selected.solutionType.endsWith("_REALTIME") && !steps.includes("FULL") ? (
            <Alert>仅增量同步不会复制现有数据，目标表需要提前准备好。</Alert>
          ) : null}
          {selected ? (
            <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
              <FieldLabel required>同步步骤</FieldLabel>
              <div className="flex flex-wrap gap-4">
                {selected.steps.map((step) => {
                  const selectedSteps = steps.includes(step)
                    ? steps.filter((value) => value !== step)
                    : [...steps, step];
                  const nextSteps = selected.steps.filter((value) => selectedSteps.includes(value));
                  return (
                    <label
                      key={step}
                      id={`${stepLabelId}-${step}`}
                      className="flex items-center gap-2 text-xs text-[#344054]"
                    >
                      <Checkbox
                        id={`${stepLabelId}-${step}-control`}
                        aria-labelledby={`${stepLabelId}-${step}`}
                        checked={steps.includes(step)}
                        disabled={!findStepCombination(selected, nextSteps)}
                        onCheckedChange={() => setSteps(nextSteps)}
                      />
                      {STEP_LABELS[step]}
                    </label>
                  );
                })}
              </div>
            </Field>
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
                nameEdited.current = true;
                setName(event.target.value);
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
          <p className="text-sm text-[#475467]">更改方案将清除草稿中已填写的连接与表映射。</p>
        </Modal>
      ) : null}
    </>
  );
}
