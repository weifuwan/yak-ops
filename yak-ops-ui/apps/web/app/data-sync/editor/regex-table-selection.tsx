import { Alert, Button, Input } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
  DataSyncMappingTemplate,
  DataSyncTableMapping,
  DataSyncTableSelection,
} from "@/service/data-sync";
import { ContentField } from "./content-layout";
import type { useResolveTableSelection } from "./use-resolve-table-selection";
import { ReplicationDefaults } from "./replication-defaults";

export function RegexTableSelection({
  dataSourceId,
  selection,
  template,
  mappings,
  capabilities,
  resolution,
  disabled,
  initialLoad,
  resolved,
  onRulesChange,
  onResolved,
}: {
  dataSourceId: string;
  selection: DataSyncTableSelection;
  template: DataSyncMappingTemplate;
  mappings: DataSyncTableMapping[];
  capabilities: DataSyncCapabilities;
  resolution: ReturnType<typeof useResolveTableSelection>;
  disabled: boolean;
  initialLoad: boolean;
  resolved: boolean;
  onRulesChange: (selection: DataSyncTableSelection, template: DataSyncMappingTemplate) => void;
  onResolved: (mappings: DataSyncTableMapping[]) => void;
}) {
  const { resolve, loading, error } = resolution;
  return (
    <fieldset disabled={loading || disabled} className="space-y-3">
      <ContentField label="库名正则" required>
        <Input
          size="small"
          variant="outlined"
          aria-label="库名正则"
          value={selection.databasePattern}
          placeholder="例如 app_.*"
          maxLength={256}
          onChange={(event) =>
            onRulesChange({ ...selection, databasePattern: event.target.value }, template)
          }
        />
      </ContentField>
      <ContentField label="表名正则" required>
        <Input
          size="small"
          variant="outlined"
          aria-label="表名正则"
          value={selection.tablePattern}
          placeholder="例如 orders_.*"
          maxLength={256}
          onChange={(event) =>
            onRulesChange({ ...selection, tablePattern: event.target.value }, template)
          }
        />
      </ContentField>
      <ContentField label="新表目标库模板">
        <Input
          size="small"
          variant="outlined"
          aria-label="新表目标库模板"
          value={template.database || ""}
          placeholder="${database}"
          onChange={(event) =>
            onRulesChange(selection, { ...template, database: event.target.value || undefined })
          }
        />
      </ContentField>
      {template.schema !== undefined ? (
        <ContentField label="新表目标 Schema 模板">
          <Input
            size="small"
            variant="outlined"
            aria-label="新表目标 Schema 模板"
            value={template.schema}
            placeholder="${schema}"
            onChange={(event) =>
              onRulesChange(selection, { ...template, schema: event.target.value })
            }
          />
        </ContentField>
      ) : null}
      <ContentField label="新表目标表模板" required>
        <Input
          size="small"
          variant="outlined"
          aria-label="新表目标表模板"
          value={template.table}
          placeholder="${table}"
          onChange={(event) => onRulesChange(selection, { ...template, table: event.target.value })}
        />
      </ContentField>
      <ReplicationDefaults
        selection={selection}
        template={template}
        capabilities={capabilities}
        initialLoad={initialLoad}
        onChange={onRulesChange}
      />
      <ContentField label="">
        <div className="flex items-center gap-3">
          <Button
            size="small"
            disabled={!dataSourceId || loading || disabled}
            loading={loading}
            onClick={() => void resolve(selection, template, onResolved)}
          >
            匹配库表
          </Button>
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            {resolved ? `已匹配 ${mappings.length} 张表` : "规则待匹配"}
          </span>
        </div>
      </ContentField>
      {error ? <Alert>{error}</Alert> : null}
    </fieldset>
  );
}
