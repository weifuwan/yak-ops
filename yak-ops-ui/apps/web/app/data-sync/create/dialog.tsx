import { Alert, Button, Field, FieldLabel, Input, Modal } from "@yak-ops/yak-ui";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { COMMON_DB_OPTIONS } from "@/app/datasource/constants";
import { useAuth } from "@/hooks/use-auth";
import { createPipelineDefinition } from "@/service/data-sync/native-pipeline";
import {
  nativeDraftKey,
  readNativeDraft,
  writeNativeDraft,
  type NativeSyncDraft,
} from "@/service/data-sync/native-draft";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { useSolutions } from "../solution/use-solutions";

export function CreateSyncTaskDialog({ onClose }: { onClose: () => void }) {
  const { currentUser } = useAuth();
  const key = nativeDraftKey(currentUser!.id);
  const previous = useRef(readNativeDraft(key));
  const [sourceType, setSourceType] = useState(previous.current?.sourceType || "");
  const [targetType, setTargetType] = useState(previous.current?.targetType || "");
  const [name, setName] = useState(previous.current?.name || "");
  const [selected, setSelected] = useState(previous.current ? "mysql:jdbc" : "");
  const navigate = useNavigate();
  const { choices, loading, error, refresh } = useSolutions(sourceType, targetType);
  const supported = choices.find((choice) =>
    choice.sourceConnector === "mysql" && choice.sinkConnector === "jdbc");
  const ready = sourceType === "MYSQL" && targetType === "MYSQL" &&
    selected === "mysql:jdbc" && Boolean(supported);

  const next = () => {
    if (!ready || !name.trim()) return;
    const draft: NativeSyncDraft =
      previous.current?.sourceType === sourceType &&
      previous.current?.targetType === targetType
        ? {
            ...previous.current,
            name: name.trim(),
          }
        : {
            sourceType, targetType, name: name.trim(),
            remark: "",
            definition: createPipelineDefinition(),
          };
    writeNativeDraft(key, draft);
    onClose();
    navigate("/data-sync/new", { state: { draftKey: key } });
  };

  return (
    <Modal open centered width={560} title="新建同步任务" onClose={onClose}
      footer={
        <>
          <Button size="small" onClick={onClose}>取消</Button>
          <Button size="small" variant="primary" disabled={!ready || !name.trim() || loading} onClick={next}>
            下一步
          </Button>
        </>
      }>
      <div className="space-y-4">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据来源类型</FieldLabel>
          <DataSyncSearchableSelect value={sourceType || null} options={COMMON_DB_OPTIONS}
            placeholder="请选择来源类型"
            onValueChange={(value) => { setSourceType(value); setSelected(""); }} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据去向类型</FieldLabel>
          <DataSyncSearchableSelect value={targetType || null} options={COMMON_DB_OPTIONS}
            placeholder="请选择去向类型"
            onValueChange={(value) => { setTargetType(value); setSelected(""); }} />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>具体类型</FieldLabel>
          <DataSyncSearchableSelect value={ready ? selected : null}
            options={supported ? [{ value: "mysql:jdbc", label: "MySQL 实时同步" }] : []}
            disabled={loading || !supported} placeholder="请选择具体类型"
            onValueChange={setSelected} />
        </Field>
        {error ? <Alert>{error}<Button size="small" onClick={refresh}>重试</Button></Alert> : null}
        {!error && !loading && sourceType && targetType && !supported ?
          <Alert>当前链路暂无可用同步方案</Alert> : null}
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
