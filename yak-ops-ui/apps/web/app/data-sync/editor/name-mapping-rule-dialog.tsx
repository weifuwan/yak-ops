import { useState } from "react";
import {
  Alert,
  Button,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import type { DataSyncNameMappingRule } from "@/service/data-sync";
import {
  validateNameMappingRule,
  applyNameMappingRule,
  createDefaultDatabaseMappingRule,
  createDefaultTableMappingRule,
  createPrefixMappingRule,
  createSuffixMappingRule,
  createReplaceMappingRule,
  createConstantMappingRule,
} from "@/service/data-sync/name-mapping-rules";
import { ContentField } from "./content-layout";

type RuleType = "database" | "schema" | "table";

interface Props {
  type: RuleType;
  value?: DataSyncNameMappingRule;
  previewContext?: {
    sourceDatasourceName?: string;
    sourceDatabase?: string;
    sourceSchema?: string;
    sourceTable?: string;
  };
  onClose: () => void;
  onApply: (rule: DataSyncNameMappingRule) => void;
}

const VARIABLE_TYPE_LABELS = {
  SOURCE_DATASOURCE: "源端数据源名",
  SOURCE_DATABASE: "源端数据库名",
  SOURCE_SCHEMA: "源端 Schema",
  SOURCE_TABLE: "源端表名",
  CONSTANT: "常量",
};

const TRANSFORM_TYPE_LABELS = {
  PREFIX: "添加前缀",
  SUFFIX: "添加后缀",
  REPLACE: "字符串替换",
};

const QUICK_TEMPLATES = {
  database: [
    { label: "保持原名", factory: () => createDefaultDatabaseMappingRule() },
    { label: "添加前缀", factory: () => createPrefixMappingRule("前缀映射", "SOURCE_DATABASE", "prd_") },
    { label: "添加后缀", factory: () => createSuffixMappingRule("后缀映射", "SOURCE_DATABASE", "_backup") },
    { label: "字符串替换", factory: () => createReplaceMappingRule("替换映射", "SOURCE_DATABASE", "dev_", "prd_") },
  ],
  schema: [
    { label: "保持原名", factory: () => ({ ...createDefaultDatabaseMappingRule(), id: "default-schema", name: "默认 Schema 映射" }) },
  ],
  table: [
    { label: "库名_表名", factory: () => createDefaultTableMappingRule() },
    { label: "保持原表名", factory: () => ({ ...createDefaultTableMappingRule(), pattern: "${0}", variables: [{ type: "SOURCE_TABLE" as const, transforms: [] }] }) },
    { label: "添加前缀", factory: () => createPrefixMappingRule("前缀映射", "SOURCE_TABLE", "sync_") },
    { label: "添加后缀", factory: () => createSuffixMappingRule("后缀映射", "SOURCE_TABLE", "_copy") },
    { label: "固定表名", factory: () => createConstantMappingRule("固定表名映射", "target_table") },
  ],
};

export function NameMappingRuleDialog({ type, value, previewContext, onClose, onApply }: Props) {
  const [rule, setRule] = useState<DataSyncNameMappingRule>(
    value || QUICK_TEMPLATES[type][0].factory(),
  );
  const [error, setError] = useState("");

  const validationError = validateNameMappingRule(rule);
  const preview = previewContext
    ? applyNameMappingRule(rule, previewContext)
    : undefined;

  const apply = () => {
    const validationError = validateNameMappingRule(rule);
    if (validationError) {
      setError(validationError);
      return;
    }
    onApply(rule);
  };

  const loadTemplate = (template: DataSyncNameMappingRule) => {
    setRule({ ...template, id: rule.id });
    setError("");
  };

  const addVariable = () => {
    setRule({
      ...rule,
      pattern: rule.pattern + "${" + rule.variables.length + "}",
      variables: [
        ...rule.variables,
        {
          type: type === "table" ? "SOURCE_TABLE" : type === "schema" ? "SOURCE_SCHEMA" : "SOURCE_DATABASE",
          transforms: [],
        },
      ],
    });
  };

  const updateVariable = (index: number, updates: Partial<typeof rule.variables[0]>) => {
    const variables = [...rule.variables];
    variables[index] = { ...variables[index], ...updates };
    setRule({ ...rule, variables });
  };

  const removeVariable = (index: number) => {
    if (rule.variables.length === 1) {
      setError("至少需要保留一个变量");
      return;
    }
    setRule({
      ...rule,
      variables: rule.variables.filter((_, i) => i !== index),
      pattern: rule.pattern.replace(`\${${index}}`, ""),
    });
  };

  const addTransform = (variableIndex: number) => {
    const variables = [...rule.variables];
    variables[variableIndex] = {
      ...variables[variableIndex],
      transforms: [
        ...(variables[variableIndex].transforms || []),
        { type: "PREFIX", value: "" },
      ],
    };
    setRule({ ...rule, variables });
  };

  const updateTransform = (
    variableIndex: number,
    transformIndex: number,
    updates: Partial<NonNullable<typeof rule.variables[0]["transforms"]>[0]>,
  ) => {
    const variables = [...rule.variables];
    const transforms = [...(variables[variableIndex].transforms || [])];
    transforms[transformIndex] = { ...transforms[transformIndex], ...updates };
    variables[variableIndex] = { ...variables[variableIndex], transforms };
    setRule({ ...rule, variables });
  };

  const removeTransform = (variableIndex: number, transformIndex: number) => {
    const variables = [...rule.variables];
    variables[variableIndex] = {
      ...variables[variableIndex],
      transforms: (variables[variableIndex].transforms || []).filter((_, i) => i !== transformIndex),
    };
    setRule({ ...rule, variables });
  };

  const typeLabel = type === "database" ? "库名" : type === "schema" ? "Schema" : "表名";

  return (
    <Modal
      open
      title={`${typeLabel}映射规则配置`}
      width={900}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply} disabled={!!validationError}>
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <ContentField label="快速模板">
          <div className="flex flex-wrap gap-2">
            {QUICK_TEMPLATES[type].map((template, index) => (
              <Button
                key={index}
                size="small"
                variant="ghost"
                onClick={() => loadTemplate(template.factory())}
              >
                {template.label}
              </Button>
            ))}
          </div>
        </ContentField>

        <ContentField label="规则名称" required>
          <Input
            size="small"
            variant="outlined"
            aria-label="规则名称"
            value={rule.name}
            maxLength={64}
            onChange={(e) => setRule({ ...rule, name: e.target.value })}
          />
        </ContentField>

        <ContentField
          label="目标名称模板"
          description="使用 ${0}, ${1} 等占位符引用下方定义的变量"
          required
        >
          <Input
            size="small"
            variant="outlined"
            aria-label="目标名称模板"
            value={rule.pattern}
            onChange={(e) => setRule({ ...rule, pattern: e.target.value })}
          />
        </ContentField>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label className="text-sm font-medium">变量配置</label>
            <Button size="small" onClick={addVariable}>
              添加变量
            </Button>
          </div>

          <div className="space-y-3">
            {rule.variables.map((variable, vIndex) => (
              <div key={vIndex} className="rounded border border-[var(--yak-border)] p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-medium text-[var(--yak-components-muted-text)]">
                    变量 ${vIndex}
                  </span>
                  <Button
                    size="small"
                    variant="ghost"
                    onClick={() => removeVariable(vIndex)}
                    disabled={rule.variables.length === 1}
                  >
                    移除
                  </Button>
                </div>

                <div className="mb-3 grid grid-cols-2 gap-3">
                  <ContentField label="变量类型">
                    <Select
                      value={variable.type}
                      onValueChange={(value) =>
                        updateVariable(vIndex, {
                          type: value as typeof variable.type,
                          value: value === "CONSTANT" ? "" : undefined,
                        })
                      }
                    >
                      <SelectTrigger size="small">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {Object.entries(VARIABLE_TYPE_LABELS).map(([value, label]) => (
                          <SelectItem key={value} value={value}>
                            {label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </ContentField>

                  {variable.type === "CONSTANT" && (
                    <ContentField label="常量值">
                      <Input
                        size="small"
                        variant="outlined"
                        value={variable.value || ""}
                        onChange={(e) => updateVariable(vIndex, { value: e.target.value })}
                      />
                    </ContentField>
                  )}
                </div>

                <div className="mb-2 flex items-center justify-between">
                  <label className="text-xs font-medium text-[var(--yak-components-muted-text)]">
                    字符串转换
                  </label>
                  <Button size="small" variant="ghost" onClick={() => addTransform(vIndex)}>
                    添加转换
                  </Button>
                </div>

                {variable.transforms && variable.transforms.length > 0 && (
                  <div className="space-y-2">
                    {variable.transforms.map((transform, tIndex) => (
                      <div key={tIndex} className="flex items-center gap-2">
                        <Select
                          value={transform.type}
                          onValueChange={(value) =>
                            updateTransform(vIndex, tIndex, {
                              type: value as typeof transform.type,
                            })
                          }
                        >
                          <SelectTrigger size="small" className="w-32">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {Object.entries(TRANSFORM_TYPE_LABELS).map(([value, label]) => (
                              <SelectItem key={value} value={value}>
                                {label}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>

                        {transform.type === "PREFIX" || transform.type === "SUFFIX" ? (
                          <Input
                            size="small"
                            variant="outlined"
                            placeholder={transform.type === "PREFIX" ? "前缀" : "后缀"}
                            value={transform.value || ""}
                            onChange={(e) => updateTransform(vIndex, tIndex, { value: e.target.value })}
                            className="flex-1"
                          />
                        ) : (
                          <>
                            <Input
                              size="small"
                              variant="outlined"
                              placeholder="匹配模式"
                              value={transform.pattern || ""}
                              onChange={(e) =>
                                updateTransform(vIndex, tIndex, { pattern: e.target.value })
                              }
                              className="flex-1"
                            />
                            <Input
                              size="small"
                              variant="outlined"
                              placeholder="替换为"
                              value={transform.replacement || ""}
                              onChange={(e) =>
                                updateTransform(vIndex, tIndex, { replacement: e.target.value })
                              }
                              className="flex-1"
                            />
                          </>
                        )}

                        <Button
                          size="small"
                          variant="ghost"
                          onClick={() => removeTransform(vIndex, tIndex)}
                        >
                          删除
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>

        {previewContext && (
          <ContentField label="预览效果">
            <div className="rounded bg-[var(--yak-background-secondary)] p-3">
              <div className="mb-1 text-xs text-[var(--yak-components-muted-text)]">
                示例输入: {type === "database" && previewContext.sourceDatabase}
                {type === "schema" && previewContext.sourceSchema}
                {type === "table" && previewContext.sourceTable}
              </div>
              <div className="text-sm font-medium">{preview || "(空)"}</div>
            </div>
          </ContentField>
        )}

        {error && <Alert variant="error">{error}</Alert>}
        {validationError && <Alert variant="warning">{validationError}</Alert>}
      </div>
    </Modal>
  );
}
