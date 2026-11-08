import { Button, Checkbox, Field, FieldLabel, Input, Modal } from "@yak-ops/yak-ui";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { COMMON_DB_OPTIONS } from "@/app/datasource/constants";
import { useAuth } from "@/hooks/use-auth";
import {
  createPipelineDefinition,
  setPipelineSteps,
} from "@/service/data-sync/native-pipeline";
import type { DataSyncScope, DataSyncStep } from "@/service/data-sync";
import {
  nativeDraftKey,
  readNativeDraft,
  writeNativeDraft,
  type NativeSyncDraft,
} from "@/service/data-sync/native-draft";
import { DataSyncSearchableSelect } from "../components/searchable-select";

export function CreateSyncTaskDialog({ onClose }: { onClose: () => void }) {
  const { currentUser } = useAuth();
  const key = nativeDraftKey(currentUser!.id);
  const previous = useRef(readNativeDraft(key));
  const [sourceType, setSourceType] = useState(previous.current?.sourceType || "");
  const [targetType, setTargetType] = useState(previous.current?.targetType || "");
  const [scope, setScope] = useState<DataSyncScope>(previous.current?.scope || "DATABASE");
  const [steps, setSteps] = useState<DataSyncStep[]>(
    previous.current?.steps || ["SCHEMA", "FULL", "INCREMENTAL"],
  );
  const [name, setName] = useState(previous.current?.name || "");
  const navigate = useNavigate();
  const supported = sourceType === "MYSQL" && targetType === "MYSQL";
  const ready =
    supported && (steps.includes("FULL") || steps.includes("INCREMENTAL"));

  const toggleStep = (step: "FULL" | "INCREMENTAL", checked: boolean) => {
    setSteps((current) => {
      const next = new Set(current);
      if (checked) next.add(step);
      else next.delete(step);
      return ["SCHEMA", "FULL", "INCREMENTAL"].filter((item) =>
        next.has(item as DataSyncStep),
      ) as DataSyncStep[];
    });
  };

  const next = () => {
    if (!ready || !name.trim()) return;
    const draft: NativeSyncDraft =
      previous.current?.sourceType === sourceType &&
      previous.current?.targetType === targetType &&
      previous.current?.scope === scope
        ? {
            ...previous.current,
            name: name.trim(),
            scope,
            steps,
            definition: setPipelineSteps(previous.current.definition, steps),
          }
        : {
            sourceType, targetType, scope, steps, name: name.trim(),
            remark: "",
            definition: createPipelineDefinition(steps),
          };
    writeNativeDraft(key, draft);
    onClose();
    navigate(
      scope === "DATABASE"
        ? "/data-sync/new/database"
        : "/data-sync/new/single-table",
      { state: { draftKey: key } },
    );
  };

  return (
    <Modal open centered width={560} title="新建同步任务" onClose={onClose}
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>取消</Button>
          <Button size="sm" variant="primary" isDisabled={!ready || !name.trim()} onClick={next}>
            下一步
          </Button>
        </>
      }>
      <div className="space-y-4">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据来源类型</FieldLabel>
          <DataSyncSearchableSelect value={sourceType || null} options={COMMON_DB_OPTIONS}
            placeholder="请选择来源类型"
            onValueChange={setSourceType} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据去向类型</FieldLabel>
          <DataSyncSearchableSelect value={targetType || null} options={COMMON_DB_OPTIONS}
            placeholder="请选择去向类型"
            onValueChange={setTargetType} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>具体类型</FieldLabel>
          <DataSyncSearchableSelect value={supported ? scope : null}
            options={supported ? [
              { value: "DATABASE", label: "整库同步" },
              { value: "SINGLE_TABLE", label: "单表同步" },
            ] : []}
            disabled={!supported} placeholder="请选择具体类型"
            onValueChange={(value) => setScope(value as DataSyncScope)} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required className="pt-1.5">同步步骤</FieldLabel>
          <div className="flex min-h-8 items-center gap-5">
            <Checkbox checked disabled aria-label="结构迁移">结构迁移</Checkbox>
            <Checkbox checked={steps.includes("FULL")}
              onCheckedChange={(checked) => toggleStep("FULL", checked === true)}>
              全量同步
            </Checkbox>
            <Checkbox checked={steps.includes("INCREMENTAL")}
              onCheckedChange={(checked) => toggleStep("INCREMENTAL", checked === true)}>
              增量同步
            </Checkbox>
          </div>
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>名称</FieldLabel>
          <Input size="small" variant="outlined" maxLength={128}
            value={name} placeholder="同步任务名称"
            onChange={(event) => setName(event.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
