import { useState } from "react";
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import {
  SCHEMA_CHANGE_BEHAVIORS,
  SCHEMA_CHANGE_EVENTS,
  type PipelineDefinition,
} from "@/service/data-sync/native-pipeline";
import type {
  DataSyncSchemaChangeBehavior,
  DataSyncSchemaChangeEvent,
} from "@/service/data-sync";
import { EditorDrawer } from "./editor-drawer";

const BEHAVIOR_LABELS: Record<DataSyncSchemaChangeBehavior, string> = {
  evolve: "严格同步",
  try_evolve: "尽力同步",
  lenient: "安全演进",
  ignore: "忽略变更",
  exception: "遇变更失败",
};

const EVENT_LABELS: Record<DataSyncSchemaChangeEvent, string> = {
  "create.table": "新建表",
  "drop.table": "删除表",
  "add.column": "新增列",
  "drop.column": "删除列",
  "rename.column": "重命名列",
  "alter.column.type": "修改列类型",
  "truncate.table": "清空表",
  "alter.table.comment": "修改表注释",
};

function effectiveExcluded(
  definition: PipelineDefinition,
): DataSyncSchemaChangeEvent[] {
  const configured = definition.sink["exclude.schema.changes"];

  if (Array.isArray(configured)) {
    return configured;
  }

  return (definition.pipeline["schema.change.behavior"] ?? "lenient") === "lenient"
    ? ["truncate.table", "drop.table"]
    : [];
}

export function AdvancedConfigDrawer({
  value,
  open,
  onApply,
  onClose,
  onClosed,
  onDirtyChange,
}: {
  value: PipelineDefinition;
  open: boolean;
  onApply: (value: PipelineDefinition) => void;
  onClose: () => void;
  onClosed: () => void;
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [form, setForm] = useState(() => structuredClone(value));

  const behavior =
    form.pipeline["schema.change.behavior"] ?? "lenient";

  const excluded = effectiveExcluded(form);

  const change = (next: PipelineDefinition) => {
    setForm(next);
    onDirtyChange(JSON.stringify(next) !== JSON.stringify(value));
  };

  const changeBehavior = (
    nextBehavior: DataSyncSchemaChangeBehavior,
  ) => {
    const next = structuredClone(form);
    next.pipeline["schema.change.behavior"] = nextBehavior;
    change(next);
  };

  const changeEvent = (
    event: DataSyncSchemaChangeEvent,
    ignored: boolean,
  ) => {
    const next = structuredClone(form);
    const values = new Set(effectiveExcluded(next));

    if (ignored) {
      values.add(event);
    } else {
      values.delete(event);
    }

    next.sink["exclude.schema.changes"] =
      SCHEMA_CHANGE_EVENTS.filter((item) => values.has(item));

    change(next);
  };

  return (
    <EditorDrawer
      title="高级配置"
      width={800}
      open={open}
      onClose={onClose}
      onClosed={onClosed}
      footer={
        <>
          <Button
            size="small"
            variant="primary"
            onClick={() => onApply(form)}
          >
            确认
          </Button>

          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="pt-4">
        <div className="space-y-3">
          <ConfigRow label="处理策略">
            <Select<DataSyncSchemaChangeBehavior>
              size="small"
              items={Object.fromEntries(
                SCHEMA_CHANGE_BEHAVIORS.map((item) => [
                  item,
                  BEHAVIOR_LABELS[item],
                ]),
              )}
              value={behavior}
              onValueChange={(next) => {
                if (next) {
                  changeBehavior(next);
                }
              }}
            >
              <SelectTrigger variant="outlined">
                <SelectValue />
              </SelectTrigger>

              <SelectContent>
                {SCHEMA_CHANGE_BEHAVIORS.map((item) => (
                  <SelectItem key={item} value={item}>
                    <SelectItemText>
                      {BEHAVIOR_LABELS[item]}
                    </SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </ConfigRow>

          <div className="h-px bg-[var(--yak-components-panel-border)]" />

          {SCHEMA_CHANGE_EVENTS.map((event) => {
            const ignored = excluded.includes(event);

            return (
              <ConfigRow
                key={event}
                label={EVENT_LABELS[event]}
                required
              >
                <Select<"normal" | "ignore">
                  size="small"
                  items={{
                    normal: "正常处理",
                    ignore: "忽略",
                  }}
                  value={ignored ? "ignore" : "normal"}
                  onValueChange={(next) => {
                    if (next) {
                      changeEvent(event, next === "ignore");
                    }
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>

                  <SelectContent>
                    <SelectItem value="normal">
                      <SelectItemText>正常处理</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>

                    <SelectItem value="ignore">
                      <SelectItemText>忽略</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  </SelectContent>
                </Select>
              </ConfigRow>
            );
          })}
        </div>
      </div>
    </EditorDrawer>
  );
}

function ConfigRow({
  label,
  required = false,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-[120px_minmax(0,1fr)] items-center gap-4">
      <div className="flex items-center justify-end text-sm">
        {required ? (
          <span className="mr-1 text-[var(--yak-color-danger)]">*</span>
        ) : null}

        <span>{label}：</span>
      </div>

      <div className="min-w-0">
        {children}
      </div>
    </div>
  );
}