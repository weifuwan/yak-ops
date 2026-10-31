import { Checkbox, FieldItem, FieldLabel, Input, Textarea } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
  DataSyncDml,
  DataSyncDmlEvent,
  DataSyncSourceOptions,
  DataSyncTableSnapshot,
} from "@/service/data-sync";
import { ContentField } from "./content-layout";

export function SnapshotFields({
  value,
  capabilities,
  initialLoad,
  onChange,
}: {
  value?: DataSyncTableSnapshot;
  capabilities: DataSyncCapabilities;
  initialLoad: boolean;
  onChange: (value: DataSyncTableSnapshot | undefined) => void;
}) {
  const patch = (next: Partial<DataSyncTableSnapshot>) => {
    const result = { ...value, ...next };
    onChange(Object.values(result).some((item) => item !== undefined) ? result : undefined);
  };
  if (!initialLoad) return null;
  return (
    <>
      <ContentField label="源端切分列">
        <Input
          size="small"
          variant="outlined"
          aria-label="源端切分列"
          value={value?.chunkKeyColumn || ""}
          onChange={(event) => patch({ chunkKeyColumn: event.target.value || undefined })}
        />
      </ContentField>
      {capabilities.snapshotSkip ? (
        <ContentField label="是否跳过全量同步">
          <label className="flex min-h-7 items-center gap-2 text-xs">
            <Checkbox
              checked={value?.skip ?? false}
              aria-label="跳过全量同步"
              disabled={Boolean(value?.where)}
              title={value?.where ? "请先清除全量条件" : undefined}
              onCheckedChange={(checked) => patch({ skip: checked || undefined })}
            />
            跳过
          </label>
        </ContentField>
      ) : null}
      {capabilities.snapshotFilter ? (
        <ContentField label="全量条件" wide>
          <Textarea
            size="small"
            variant="outlined"
            aria-label="全量条件"
            className="!min-h-16 font-mono text-xs"
            value={value?.where || ""}
            disabled={Boolean(value?.skip)}
            title={value?.skip ? "跳过全量同步时不设置全量条件" : undefined}
            placeholder="不包含 WHERE 关键字"
            onValueChange={(where) => patch({ where: String(where) || undefined })}
          />
        </ContentField>
      ) : null}
    </>
  );
}

export function DmlFields({
  value,
  onChange,
}: {
  value?: DataSyncDml;
  onChange: (value: DataSyncDml) => void;
}) {
  const events = value?.events ?? ["INSERT", "UPDATE", "DELETE"];
  return (
    <ContentField label="DML 规则">
      <div className="flex min-h-7 flex-wrap items-center gap-4">
        {(["INSERT", "UPDATE", "DELETE"] as DataSyncDmlEvent[]).map((event) => (
          <FieldItem key={event}>
            <FieldLabel className="flex items-center gap-2 text-xs font-normal">
              <Checkbox
                checked={events.includes(event)}
                onCheckedChange={(checked) =>
                  onChange({
                    events: checked ? [...events, event] : events.filter((item) => item !== event),
                  })
                }
              />
              {event}
            </FieldLabel>
          </FieldItem>
        ))}
      </div>
    </ContentField>
  );
}

export function SourceEngineFields({
  value,
  keys,
  onChange,
}: {
  value?: DataSyncSourceOptions;
  keys: string[];
  onChange: (value: DataSyncSourceOptions | undefined) => void;
}) {
  return (
    <>
      {[
        { key: "fetchSize" as const, label: "读取批次大小", max: 10000, defaultValue: 500 },
        {
          key: "queryTimeoutSeconds" as const,
          label: "查询超时（秒）",
          max: 300,
          defaultValue: 60,
        },
      ]
        .filter((item) => keys.includes(item.key))
        .map((item) => (
          <ContentField key={item.key} label={item.label}>
            <Input
              size="small"
              variant="outlined"
              type="number"
              min={1}
              max={item.max}
              aria-label={item.label}
              placeholder={String(item.defaultValue)}
              value={value?.[item.key] ?? ""}
              onChange={(event) => {
                const next = {
                  ...value,
                  [item.key]: event.target.value ? Number(event.target.value) : undefined,
                };
                onChange(
                  Object.values(next).some((option) => option !== undefined) ? next : undefined,
                );
              }}
            />
          </ContentField>
        ))}
    </>
  );
}

export function SinkEngineFields({
  batchSize,
  keys,
  disabled = false,
  onChange,
}: {
  batchSize?: number;
  keys: string[];
  disabled?: boolean;
  onChange: (value: number | undefined) => void;
}) {
  return keys.includes("batchSize") ? (
    <ContentField label="写入批次大小">
      <Input
        size="small"
        variant="outlined"
        type="number"
        min={1}
        max={10000}
        aria-label="写入批次大小"
        placeholder="500"
        disabled={disabled}
        value={batchSize ?? ""}
        onChange={(event) => onChange(event.target.value ? Number(event.target.value) : undefined)}
      />
    </ContentField>
  ) : null;
}
