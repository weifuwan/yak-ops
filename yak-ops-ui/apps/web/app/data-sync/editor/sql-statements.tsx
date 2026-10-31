import { Button, FieldItem, Textarea } from "@yak-ops/yak-ui";
import { Plus, X } from "lucide-react";

export function SqlStatements({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string[];
  onChange: (statements: string[]) => void;
}) {
  const entries = value.length ? value : [""];
  const change = (next: string[]) => onChange(next.every((text) => !text) ? [] : next);
  return (
    <div className="space-y-1.5">
      <p className="text-xs text-[var(--yak-components-muted-text)]">
        每项填写一条针对当前目标表的 INSERT、UPDATE、DELETE 或 TRUNCATE。
      </p>
      {entries.map((statement, index) => (
        <FieldItem key={index} className="flex items-start gap-1">
          <Textarea
            size="small"
            variant="outlined"
            className="!min-h-16 font-mono text-xs"
            aria-label={`${label} ${index + 1}`}
            placeholder={
              label === "导入前准备语句" ? "同步开始前执行的 SQL" : "同步完成后执行的 SQL"
            }
            value={statement}
            spellCheck={false}
            onValueChange={(text) =>
              change(entries.map((item, position) => (position === index ? String(text) : item)))
            }
          />
          {value.length > 1 ? (
            <Button
              size="small"
              variant="ghost"
              aria-label={`移除${label} ${index + 1}`}
              onClick={() => change(value.filter((_, position) => position !== index))}
            >
              <X size={14} />
            </Button>
          ) : null}
        </FieldItem>
      ))}
      <Button
        size="small"
        variant="ghost"
        className="px-0 text-xs text-[var(--yak-color-primary)]"
        disabled={!entries[entries.length - 1]?.trim()}
        onClick={() => onChange([...entries, ""])}
      >
        <Plus size={13} />
        添加
      </Button>
    </div>
  );
}
