import type {
  DataSyncNameMappingRule,
  DataSyncTableMapping,
  DataSyncTablePath,
} from "./types";
import { applyMappingRules } from "./name-mapping-rules";

/**
 * Refresh target mappings by applying mapping rules to source tables.
 * This recreates target database/schema/table names based on current rules.
 */
export function refreshMappingsWithRules(
  mappings: readonly DataSyncTableMapping[],
  context: {
    sourceDatasourceName?: string;
    databaseRule?: DataSyncNameMappingRule;
    schemaRule?: DataSyncNameMappingRule;
    tableRule?: DataSyncNameMappingRule;
    preserveExisting?: boolean; // If true, only update mappings without custom names
  },
): DataSyncTableMapping[] {
  return mappings.map((mapping) => {
    // If preserveExisting is true and mapping has custom rules, skip it
    if (
      context.preserveExisting &&
      (mapping.databaseMappingRuleId ||
        mapping.schemaMappingRuleId ||
        mapping.tableMappingRuleId)
    ) {
      return mapping;
    }

    const newTarget = applyMappingRules(mapping.source, {
      sourceDatasourceName: context.sourceDatasourceName,
      databaseRule: context.databaseRule,
      schemaRule: context.schemaRule,
      tableRule: context.tableRule,
    });

    return {
      ...mapping,
      target: {
        ...mapping.target,
        database: newTarget.database,
        schema: newTarget.schema,
        table: newTarget.table,
      },
      databaseMappingRuleId: context.databaseRule?.id,
      schemaMappingRuleId: context.schemaRule?.id,
      tableMappingRuleId: context.tableRule?.id,
    };
  });
}

/**
 * Apply mapping rules to a single source table to generate target path.
 */
export function generateTargetMapping(
  source: DataSyncTablePath,
  context: {
    sourceDatasourceName?: string;
    databaseRule?: DataSyncNameMappingRule;
    schemaRule?: DataSyncNameMappingRule;
    tableRule?: DataSyncNameMappingRule;
  },
): DataSyncTablePath {
  return applyMappingRules(source, context);
}

/**
 * Batch update mappings with a specific rule type.
 */
export function batchUpdateMappingRule(
  mappings: readonly DataSyncTableMapping[],
  selectedKeys: readonly string[],
  ruleType: "database" | "schema" | "table",
  rule: DataSyncNameMappingRule | undefined,
  context: {
    sourceDatasourceName?: string;
  },
): DataSyncTableMapping[] {
  const selectedSet = new Set(selectedKeys);

  return mappings.map((mapping) => {
    const key = [mapping.source.database, mapping.source.schema, mapping.source.table]
      .filter(Boolean)
      .join(".");

    if (!selectedSet.has(key)) return mapping;

    const ruleContext = {
      sourceDatasourceName: context.sourceDatasourceName,
      sourceDatabase: mapping.source.database,
      sourceSchema: mapping.source.schema,
      sourceTable: mapping.source.table,
    };

    const updates: Partial<DataSyncTableMapping> = {};

    if (ruleType === "database" && rule) {
      updates.target = {
        ...mapping.target,
        database: applyMappingRules(mapping.source, {
          ...context,
          databaseRule: rule,
        }).database,
      };
      updates.databaseMappingRuleId = rule.id;
    } else if (ruleType === "schema" && rule) {
      updates.target = {
        ...mapping.target,
        schema: applyMappingRules(mapping.source, {
          ...context,
          schemaRule: rule,
        }).schema,
      };
      updates.schemaMappingRuleId = rule.id;
    } else if (ruleType === "table" && rule) {
      updates.target = {
        ...mapping.target,
        table: applyMappingRules(mapping.source, {
          ...context,
          tableRule: rule,
        }).table,
      };
      updates.tableMappingRuleId = rule.id;
    }

    return { ...mapping, ...updates };
  });
}

/**
 * Detect conflicts in target mappings (multiple sources mapping to same target).
 */
export function detectMappingConflicts(
  mappings: readonly DataSyncTableMapping[],
): Map<string, DataSyncTableMapping[]> {
  const targetGroups = new Map<string, DataSyncTableMapping[]>();

  mappings.forEach((mapping) => {
    const targetKey = [mapping.target.database, mapping.target.schema, mapping.target.table]
      .filter(Boolean)
      .join(".");

    if (!targetGroups.has(targetKey)) {
      targetGroups.set(targetKey, []);
    }
    targetGroups.get(targetKey)!.push(mapping);
  });

  // Filter to only conflicts (more than one source)
  const conflicts = new Map<string, DataSyncTableMapping[]>();
  targetGroups.forEach((group, key) => {
    if (group.length > 1) {
      conflicts.set(key, group);
    }
  });

  return conflicts;
}

/**
 * Validate that all target mappings are unique and valid.
 */
export function validateMappings(
  mappings: readonly DataSyncTableMapping[],
): string | undefined {
  if (mappings.length === 0) return "至少需要一张表";

  // Check for empty target tables
  const emptyTargets = mappings.filter((m) => !m.target.table.trim());
  if (emptyTargets.length > 0) {
    return `${emptyTargets.length} 张表的目标表名为空`;
  }

  // Check for conflicts
  const conflicts = detectMappingConflicts(mappings);
  if (conflicts.size > 0) {
    const conflictCount = Array.from(conflicts.values()).reduce(
      (sum, group) => sum + group.length,
      0,
    );
    return `${conflictCount} 张表存在目标冲突`;
  }

  return undefined;
}
