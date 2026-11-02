import { Checkbox, Input } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
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
              title={value?.where ? "已有全量条件的任务不能同时跳过全量" : undefined}
              onCheckedChange={(checked) => patch({ skip: checked || undefined })}
            />
            跳过
          </label>
        </ContentField>
      ) : null}
      {value?.where ? (
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          已保存的全量条件仍会生效，当前页面不提供编辑。
        </p>
      ) : null}
    </>
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
