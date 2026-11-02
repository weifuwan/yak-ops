import { useState } from "react";
import {
  Alert,
  Button,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import type { DataSyncNameMappingRule, DataSyncTableMapping } from "@/service/data-sync";
import {
  createDefaultDatabaseMappingRule,
  createDefaultSchemaMappingRule,
  createDefaultTableMappingRule,
} from "@/service/data-sync/name-mapping-rules";
import { batchUpdateMappingRule } from "@/service/data-sync/mapping-refresh";
import { ContentField } from "./content-layout";
import { NameMappingRuleDialog } from "./name-mapping-rule-dialog";

interface Props {
  mappings: DataSyncTableMapping[];
  selectedKeys: string[];
  sourceDatasourceName?: string;
  supportsDatabase: boolean;
  supportsSchema: boolean;
  onClose: () => void;
  onApply: (mappings: DataSyncTableMapping[]) => void;
}

type RuleType = "database" | "schema" | "table";

interface RuleSelection {
  type: RuleType;
  rule?: DataSyncNameMappingRule;
}

export function BatchMappingRulesDialog({
  mappings,
  selectedKeys,
  sourceDatasourceName,
  supportsDatabase,
  supportsSchema,
  onClose,
  onApply,
}: Props) {
  const [selections, setSelections] = useState<RuleSelection[]>([]);
  const [editingRule, setEditingRule] = useState<{
    type: RuleType;
    rule?: DataSyncNameMappingRule;
  }>();
  const [error, setError] = useState("");

  const apply = () => {
    try {
      let result = mappings;

      selections.forEach((selection) => {
        if (selection.rule) {
          result = batchUpdateMappingRule(result, selectedKeys, selection.type, selection.rule, {
            sourceDatasourceName,
          });
        }
      });

      onApply(result);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "批量修改失败");
    }
  };

  const addSelection = (type: RuleType) => {
    const defaultRule =
      type === "database"
        ? createDefaultDatabaseMappingRule()
        : type === "schema"
          ? createDefaultSchemaMappingRule()
          : createDefaultTableMappingRule();

    setSelections([...selections, { type, rule: defaultRule }]);
  };

  const updateSelection = (index: number, rule: DataSyncNameMappingRule) => {
    const updated = [...selections];
    updated[index] = { ...updated[index], rule };
    setSelections(updated);
  };

  const removeSelection = (index: number) => {
    setSelections(selections.filter((_, i) => i !== index));
  };

  const getTypeLabel = (type: RuleType) =>
    type === "database" ? "目标库名" : type === "schema" ? "目标 Schema" : "目标表名";

  // Get preview context from first selected mapping
  const previewMapping = mappings.find((m) =>
    selectedKeys.includes([m.source.database, m.source.schema, m.source.table].filter(Boolean).join(".")),
  );
  const previewContext = previewMapping
    ? {
        sourceDatasourceName,
        sourceDatabase: previewMapping.source.database,
        sourceSchema: previewMapping.source.schema,
        sourceTable: previewMapping.source.table,
      }
    : undefined;

  return (
    <Modal
      open
      title="批量修改映射规则"
      width={800}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>
            应用到 {selectedKeys.length} 张表
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Alert variant="info">
          已选择 {selectedKeys.length} 张表，将批量应用以下映射规则
        </Alert>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label className="text-sm font-medium">映射规则</label>
            <div className="flex gap-2">
              {supportsDatabase && (
                <Button
                  size="small"
                  variant="ghost"
                  onClick={() => addSelection("database")}
                  disabled={selections.some((s) => s.type === "database")}
                >
                  配置库名映射
                </Button>
              )}
              {supportsSchema && (
                <Button
                  size="small"
                  variant="ghost"
                  onClick={() => addSelection("schema")}
                  disabled={selections.some((s) => s.type === "schema")}
                >
                  配置 Schema 映射
                </Button>
              )}
              <Button
                size="small"
                variant="ghost"
                onClick={() => addSelection("table")}
                disabled={selections.some((s) => s.type === "table")}
              >
                配置表名映射
              </Button>
            </div>
          </div>

          {selections.length > 0 ? (
            <div className="space-y-2">
              {selections.map((selection, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between rounded border border-[var(--yak-border)] p-3"
                >
                  <div className="flex-1">
                    <div className="mb-1 text-sm font-medium">{getTypeLabel(selection.type)}</div>
                    {selection.rule ? (
                      <div className="text-xs text-[var(--yak-components-muted-text)]">
                        {selection.rule.name}
                      </div>
                    ) : (
                      <div className="text-xs text-[var(--yak-components-muted-text)]">
                        未配置规则
                      </div>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <Button
                      size="small"
                      variant="ghost"
                      onClick={() =>
                        setEditingRule({
                          type: selection.type,
                          rule: selection.rule,
                        })
                      }
                    >
                      编辑规则
                    </Button>
                    <Button size="small" variant="ghost" onClick={() => removeSelection(index)}>
                      移除
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded border border-dashed border-[var(--yak-border)] p-8 text-center text-sm text-[var(--yak-components-muted-text)]">
              请选择需要批量修改的映射类型
            </div>
          )}
        </div>

        {error && <Alert variant="error">{error}</Alert>}
      </div>

      {editingRule && (
        <NameMappingRuleDialog
          type={editingRule.type}
          value={editingRule.rule}
          previewContext={previewContext}
          onClose={() => setEditingRule(undefined)}
          onApply={(rule) => {
            const index = selections.findIndex((s) => s.type === editingRule.type);
            if (index >= 0) {
              updateSelection(index, rule);
            }
            setEditingRule(undefined);
          }}
        />
      )}
    </Modal>
  );
}
