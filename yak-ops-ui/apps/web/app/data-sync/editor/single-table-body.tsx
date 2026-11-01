import {
  Button,
  Collapsible,
  CollapsiblePanel,
  CollapsibleTrigger,
  Input,
  Textarea,
} from "@yak-ops/yak-ui";
import { useState } from "react";
import type {
  DataSyncCapabilities,
  DataSyncTransform,
  DataSyncTypeMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField, ContentSection } from "./content-layout";
import { TablePicker, type TablePickerProps } from "./table-picker";
import { IncrementalFields } from "./incremental-fields";
import { FieldMappingSection } from "./field-mapping-section";
import { SourcePreview } from "./source-preview";
import { SqlStatements } from "./sql-statements";
import { selectedTableKey } from "./table-catalog";
import type { EditorForm, EditorSink, EditorSource } from "./types";
import { SourceEngineFields, SinkEngineFields } from "./table-option-fields";
import { TypeMappingDialog } from "./type-mapping-dialog";

interface Props {
  form: EditorForm;
  source: TablePickerProps;
  target: TablePickerProps;
  capabilities?: DataSyncCapabilities;
  writeModes: DataSyncWriteMode[];
  useIncremental: boolean;
  initialLoad: boolean;
  patchSource: <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => void;
  patchSink: <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => void;
  onTransformsChange: (transforms: DataSyncTransform[]) => void;
  onTypeMappingsChange: (rules: DataSyncTypeMapping[]) => void;
}

export function SingleTableBatchBody({
  form,
  source,
  target,
  capabilities,
  writeModes,
  useIncremental,
  initialLoad,
  patchSource,
  patchSink,
  onTransformsChange,
  onTypeMappingsChange,
}: Props) {
  const [types, setTypes] = useState(false);
  return (
    <>
      <ContentSection id="source" title="数据来源">
        <TablePicker {...source} />
        {capabilities?.sourceRead ? (
          <ContentField label="数据过滤" wide>
            <Textarea
              size="small"
              variant="outlined"
              className="!min-h-16 font-mono text-xs"
              aria-label="数据过滤"
              placeholder="填写 WHERE 条件，不包含 WHERE 关键字"
              value={form.source.where}
              spellCheck={false}
              onValueChange={(value) => patchSource("where", String(value))}
            />
          </ContentField>
        ) : null}
        {capabilities?.explicitSplitKey ? (
          <ContentField label="切分键">
            <Input
              size="small"
              variant="outlined"
              aria-label="切分键"
              value={form.source.splitKey}
              placeholder="选择用于并行读取的字段"
              onChange={(event) => patchSource("splitKey", event.target.value)}
            />
          </ContentField>
        ) : null}
        {useIncremental ? (
          <ContentField label="增量设置" required>
            <IncrementalFields
              value={form.source.incremental}
              initialLoad={initialLoad}
              onChange={(value) => patchSource("incremental", value)}
            />
          </ContentField>
        ) : null}
        <ContentField label="">
          <SourcePreview
            dataSourceId={form.source.dataSourceId}
            query={{
              database: form.source.database || undefined,
              schema: form.source.schema || undefined,
              table: form.source.table,
              where: form.source.where.trim() || undefined,
            }}
            supported={Boolean(capabilities?.dataPreview)}
          />
        </ContentField>
        {capabilities?.sourceEngineKeys.length ? (
          <Collapsible>
            <CollapsibleTrigger className="text-xs text-[var(--yak-color-primary)]">
              来源高级配置
            </CollapsibleTrigger>
            <CollapsiblePanel>
              <div className="space-y-3 pt-3">
                <SourceEngineFields
                  value={form.source.options}
                  keys={capabilities.sourceEngineKeys}
                  onChange={(value) => patchSource("options", value)}
                />
              </div>
            </CollapsiblePanel>
          </Collapsible>
        ) : null}
      </ContentSection>
      <ContentSection id="target" title="数据去向">
        <TablePicker {...target} />
        {capabilities?.sqlHooks ? (
          <>
            <ContentField label="导入前准备语句">
              <SqlStatements
                label="导入前准备语句"
                value={form.sink.preSql}
                onChange={(value) => patchSink("preSql", value)}
              />
            </ContentField>
            <ContentField label="导入后完成语句">
              <SqlStatements
                label="导入后完成语句"
                value={form.sink.postSql}
                onChange={(value) => patchSink("postSql", value)}
              />
            </ContentField>
          </>
        ) : null}
        <ContentField label="主键冲突" required>
          <DataSyncSearchableSelect
            ariaLabel="主键冲突"
            value={form.sink.writeMode}
            placeholder="请选择主键冲突策略"
            options={writeModes.map((value) => ({
              value,
              label:
                value === "UPSERT"
                  ? "按主键更新"
                  : value === "REPLACE"
                    ? "replace into（替换冲突记录）"
                    : "insert into（主键冲突时报错）",
            }))}
            disabled={writeModes.length < 2}
            onValueChange={(value) => patchSink("writeMode", value as DataSyncWriteMode)}
          />
        </ContentField>
        {capabilities?.sinkEngineKeys.length ? (
          <Collapsible>
            <CollapsibleTrigger className="text-xs text-[var(--yak-color-primary)]">
              去向高级配置
            </CollapsibleTrigger>
            <CollapsiblePanel>
              <div className="space-y-3 pt-3">
                <SinkEngineFields
                  batchSize={form.sink.batchSize}
                  keys={capabilities.sinkEngineKeys}
                  onChange={(value) => patchSink("batchSize", value)}
                />
              </div>
            </CollapsiblePanel>
          </Collapsible>
        ) : null}
      </ContentSection>
      <FieldMappingSection
        source={form.source}
        target={form.sink}
        targetTableExists={Boolean(
          selectedTableKey(
            target.catalog.tables,
            form.sink.database,
            form.sink.schema,
            form.sink.table,
          ),
        )}
        value={form.transforms}
        onChange={onTransformsChange}
        extra={
          capabilities?.typeMapping ? (
            <Button
              size="small"
              onClick={(event) => {
                event.stopPropagation();
                setTypes(true);
              }}
            >
              编辑字段类型映射
            </Button>
          ) : undefined
        }
      />
      {types && capabilities ? (
        <TypeMappingDialog
          value={form.typeMappings}
          rules={capabilities.typeMappingRules}
          onClose={() => setTypes(false)}
          onApply={(value) => {
            onTypeMappingsChange(value);
            setTypes(false);
          }}
        />
      ) : null}
    </>
  );
}
