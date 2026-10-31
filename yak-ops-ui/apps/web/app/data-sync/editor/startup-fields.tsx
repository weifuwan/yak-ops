import { Field, FieldLabel, Input } from "@yak-ops/yak-ui";
import type { DataSyncStartupOptions } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";

export function startupConfigurationError(value?: DataSyncStartupOptions): string {
  if (!value) return "";
  if (
    [value.pos, value.timestampMillis, value.skipEvents, value.skipRows].some(
      (number) => number !== undefined && (!Number.isSafeInteger(number) || number < 0),
    )
  )
    return "启动位置的数字必须是非负整数";
  if (
    value.mode === "SPECIFIC_OFFSETS" &&
    !value.gtidSet?.trim() &&
    !(value.file?.trim() && value.pos !== undefined)
  )
    return "请填写 GTID，或填写日志文件和日志位置";
  if (value.mode === "TIMESTAMP" && value.timestampMillis === undefined) return "请填写启动时间戳";
  return "";
}

export function StartupFields({
  value,
  initialLoad,
  onChange,
}: {
  value?: DataSyncStartupOptions;
  initialLoad: boolean;
  onChange: (value: DataSyncStartupOptions) => void;
}) {
  const startup = value || {
    mode: initialLoad ? ("INITIAL" as const) : ("LATEST_OFFSET" as const),
  };
  const patch = (next: Partial<DataSyncStartupOptions>) => onChange({ ...startup, ...next });
  const numberField = (
    key: "pos" | "timestampMillis" | "skipEvents" | "skipRows",
    label: string,
  ) => (
    <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3" key={key}>
      <FieldLabel>{label}</FieldLabel>
      <Input
        aria-label={label}
        size="small"
        variant="outlined"
        inputMode="numeric"
        value={startup[key] ?? ""}
        onChange={(event) =>
          patch({ [key]: event.target.value === "" ? undefined : Number(event.target.value) })
        }
      />
    </Field>
  );
  return (
    <div className="space-y-3">
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>启动位置</FieldLabel>
        <DataSyncSearchableSelect
          ariaLabel="启动位置"
          placeholder="请选择启动位置"
          value={startup.mode}
          disabled={initialLoad}
          options={
            initialLoad
              ? [{ value: "INITIAL", label: "全量后持续增量" }]
              : [
                  { value: "LATEST_OFFSET", label: "最新位点" },
                  { value: "EARLIEST_OFFSET", label: "最早位点" },
                  { value: "SPECIFIC_OFFSETS", label: "指定位点" },
                  { value: "TIMESTAMP", label: "指定时间" },
                ]
          }
          onValueChange={(mode) => onChange({ mode: mode as DataSyncStartupOptions["mode"] })}
        />
      </Field>
      {startup.mode === "SPECIFIC_OFFSETS" ? (
        <>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>GTID</FieldLabel>
            <Input
              aria-label="GTID"
              size="small"
              variant="outlined"
              value={startup.gtidSet || ""}
              onChange={(event) => patch({ gtidSet: event.target.value || undefined })}
            />
          </Field>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>日志文件</FieldLabel>
            <Input
              aria-label="日志文件"
              size="small"
              variant="outlined"
              value={startup.file || ""}
              onChange={(event) => patch({ file: event.target.value || undefined })}
            />
          </Field>
          {numberField("pos", "日志位置")}
          {numberField("skipEvents", "跳过事件")}
          {numberField("skipRows", "跳过行")}
        </>
      ) : null}
      {startup.mode === "TIMESTAMP" ? numberField("timestampMillis", "启动时间戳(ms)") : null}
    </div>
  );
}
