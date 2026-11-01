import { useState } from "react";
import { Alert, Button, Modal, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { DataSyncTransform } from "@/service/data-sync";
import { parameterFingerprint } from "@/service/data-sync/versions";
import { transformEditingError } from "@/service/data-sync/transforms";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { TransformExpressionFields } from "./transform-expression-fields";

export function TransformRulesDialog({
  value,
  selector,
  onApply,
  onClose,
}: {
  value: DataSyncTransform[];
  selector?: string;
  onApply: (value: DataSyncTransform[]) => void;
  onClose: () => void;
}) {
  const [initial] = useState(() =>
    structuredClone(value.length ? value : selector ? [{ "source-table": selector }] : []),
  );
  const [rules, setRules] = useState(initial);
  const [active, setActive] = useState(0);
  const [error, setError] = useState("");
  useUnsavedChanges(parameterFingerprint(rules) !== parameterFingerprint(initial));
  const columns: TableColumns<DataSyncTransform & { index: number }> = [
    { key: "source", title: "来源表规则", render: (_, row) => row["source-table"] },
    {
      key: "projection",
      title: "字段表达式",
      render: (_, row) => (
        <span className="block max-w-80 truncate font-mono text-xs">{row.projection || "*"}</span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      render: (_, row) => (
        <div className="flex gap-1">
          <Button size="small" variant="ghost" onClick={() => setActive(row.index)}>
            编辑
          </Button>
          <Button
            size="small"
            variant="ghost"
            onClick={() => {
              setRules(rules.filter((_, index) => index !== row.index));
              setActive(0);
            }}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];
  return (
    <Modal
      open
      title={selector ? "目标表字段赋值" : "字段表达式配置"}
      width={860}
      onClose={onClose}
      footer={
        <>
          <Button
            size="small"
            variant="primary"
            onClick={() => {
              const issue = transformEditingError(rules);
              if (issue) {
                setError(issue);
                return;
              }
              onApply(
                !value.length && parameterFingerprint(rules) === parameterFingerprint(initial)
                  ? value
                  : rules,
              );
            }}
          >
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {!selector || rules.length > 1 ? (
          <Table
            columns={columns}
            dataSource={rules.map((rule, index) => ({ ...rule, index }))}
            rowKey="index"
            size="small"
            pagination={false}
            emptyText="没有转换规则"
          />
        ) : null}
        {rules[active] ? (
          <TransformExpressionFields
            rule={rules[active]}
            showSelector={!selector}
            onChange={(patch) =>
              setRules(
                rules.map((rule, index) => (index === active ? { ...rule, ...patch } : rule)),
              )
            }
          />
        ) : null}
        <Button
          size="small"
          onClick={() => {
            setActive(rules.length);
            setRules([...rules, { "source-table": selector || "" }]);
          }}
        >
          添加规则
        </Button>
        {error ? <Alert>{error}</Alert> : null}
      </div>
    </Modal>
  );
}
