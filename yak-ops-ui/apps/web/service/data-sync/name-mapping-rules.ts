import type { DataSyncNameMappingRule, DataSyncTablePath } from "./types";

/**
 * Apply a name mapping rule to generate target identifier.
 * Supports variables: SOURCE_DATASOURCE, SOURCE_DATABASE, SOURCE_SCHEMA, SOURCE_TABLE, CONSTANT.
 * Supports transforms: PREFIX, SUFFIX, REPLACE.
 */
export function applyNameMappingRule(
  rule: DataSyncNameMappingRule,
  context: {
    sourceDatasourceName?: string;
    sourceDatabase?: string;
    sourceSchema?: string;
    sourceTable?: string;
  },
): string {
  let result = rule.pattern;

  rule.variables.forEach((variable, index) => {
    let value = "";

    // Get base value
    switch (variable.type) {
      case "SOURCE_DATASOURCE":
        value = context.sourceDatasourceName || "";
        break;
      case "SOURCE_DATABASE":
        value = context.sourceDatabase || "";
        break;
      case "SOURCE_SCHEMA":
        value = context.sourceSchema || "";
        break;
      case "SOURCE_TABLE":
        value = context.sourceTable || "";
        break;
      case "CONSTANT":
        value = variable.value || "";
        break;
    }

    // Apply transforms
    if (variable.transforms) {
      variable.transforms.forEach((transform) => {
        switch (transform.type) {
          case "PREFIX":
            value = (transform.value || "") + value;
            break;
          case "SUFFIX":
            value = value + (transform.value || "");
            break;
          case "REPLACE":
            if (transform.pattern) {
              value = value.replace(
                new RegExp(transform.pattern, "g"),
                transform.replacement || "",
              );
            }
            break;
        }
      });
    }

    // Replace placeholder in pattern
    result = result.replace(`\${${index}}`, value);
  });

  return result;
}

/**
 * Create default database/schema/table mapping rules based on DataWorks patterns.
 */
export function createDefaultDatabaseMappingRule(): DataSyncNameMappingRule {
  return {
    id: "default-database",
    name: "默认库名映射",
    pattern: "${0}",
    variables: [
      {
        type: "SOURCE_DATABASE",
        transforms: [],
      },
    ],
  };
}

export function createDefaultSchemaMappingRule(): DataSyncNameMappingRule {
  return {
    id: "default-schema",
    name: "默认 Schema 映射",
    pattern: "${0}",
    variables: [
      {
        type: "SOURCE_SCHEMA",
        transforms: [],
      },
    ],
  };
}

export function createDefaultTableMappingRule(): DataSyncNameMappingRule {
  return {
    id: "default-table",
    name: "默认表名映射 (库名_表名)",
    pattern: "${0}_${1}",
    variables: [
      {
        type: "SOURCE_DATABASE",
        transforms: [],
      },
      {
        type: "SOURCE_TABLE",
        transforms: [],
      },
    ],
  };
}

/**
 * Create a mapping rule with prefix.
 */
export function createPrefixMappingRule(
  name: string,
  sourceType: "SOURCE_DATABASE" | "SOURCE_SCHEMA" | "SOURCE_TABLE",
  prefix: string,
): DataSyncNameMappingRule {
  return {
    id: `prefix-${sourceType.toLowerCase()}-${Date.now()}`,
    name,
    pattern: "${0}",
    variables: [
      {
        type: sourceType,
        transforms: [
          {
            type: "PREFIX",
            value: prefix,
          },
        ],
      },
    ],
  };
}

/**
 * Create a mapping rule with suffix.
 */
export function createSuffixMappingRule(
  name: string,
  sourceType: "SOURCE_DATABASE" | "SOURCE_SCHEMA" | "SOURCE_TABLE",
  suffix: string,
): DataSyncNameMappingRule {
  return {
    id: `suffix-${sourceType.toLowerCase()}-${Date.now()}`,
    name,
    pattern: "${0}",
    variables: [
      {
        type: sourceType,
        transforms: [
          {
            type: "SUFFIX",
            value: suffix,
          },
        ],
      },
    ],
  };
}

/**
 * Create a mapping rule with string replacement.
 */
export function createReplaceMappingRule(
  name: string,
  sourceType: "SOURCE_DATABASE" | "SOURCE_SCHEMA" | "SOURCE_TABLE",
  pattern: string,
  replacement: string,
): DataSyncNameMappingRule {
  return {
    id: `replace-${sourceType.toLowerCase()}-${Date.now()}`,
    name,
    pattern: "${0}",
    variables: [
      {
        type: sourceType,
        transforms: [
          {
            type: "REPLACE",
            pattern,
            replacement,
          },
        ],
      },
    ],
  };
}

/**
 * Create a constant mapping rule (many-to-one).
 */
export function createConstantMappingRule(name: string, constantValue: string): DataSyncNameMappingRule {
  return {
    id: `constant-${Date.now()}`,
    name,
    pattern: "${0}",
    variables: [
      {
        type: "CONSTANT",
        value: constantValue,
      },
    ],
  };
}

/**
 * Validate a name mapping rule.
 */
export function validateNameMappingRule(rule: DataSyncNameMappingRule): string | undefined {
  if (!rule.name.trim()) return "规则名称不能为空";
  if (!rule.pattern.trim()) return "模板不能为空";
  if (rule.variables.length === 0) return "至少需要一个变量";

  for (const variable of rule.variables) {
    if (variable.type === "CONSTANT" && !variable.value?.trim()) {
      return "常量变量值不能为空";
    }
    if (variable.transforms) {
      for (const transform of variable.transforms) {
        if (transform.type === "REPLACE" && !transform.pattern) {
          return "替换模式不能为空";
        }
      }
    }
  }

  return undefined;
}

/**
 * Apply mapping rules to generate target path from source path.
 */
export function applyMappingRules(
  source: DataSyncTablePath,
  context: {
    sourceDatasourceName?: string;
    databaseRule?: DataSyncNameMappingRule;
    schemaRule?: DataSyncNameMappingRule;
    tableRule?: DataSyncNameMappingRule;
  },
): DataSyncTablePath {
  const ruleContext = {
    sourceDatasourceName: context.sourceDatasourceName,
    sourceDatabase: source.database,
    sourceSchema: source.schema,
    sourceTable: source.table,
  };

  return {
    database: context.databaseRule
      ? applyNameMappingRule(context.databaseRule, ruleContext)
      : source.database,
    schema: context.schemaRule
      ? applyNameMappingRule(context.schemaRule, ruleContext)
      : source.schema,
    table: context.tableRule
      ? applyNameMappingRule(context.tableRule, ruleContext)
      : source.table,
  };
}
