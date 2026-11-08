import { ContentSection } from "./content-layout";
import { DataSourceSection } from "./data-source-section";
import { TaskEditorFrame } from "./task-editor-frame";
import { useTaskEditorController } from "./use-task-editor-controller";
import type { TaskEditorProps } from "./editor-types";
import {
  pipelineDatabaseRoute,
  setPipelineDatabaseScope,
  setPipelineDatasource,
  setPipelineSinkDatabase,
} from "@/service/data-sync/native-pipeline";

const ANCHORS = [
  { id: "datasource", label: "数据源" },
  { id: "source", label: "同步范围" },
  { id: "target", label: "目标映射" },
];

export function DatabaseTaskEditor(props: TaskEditorProps) {
  const editor = useTaskEditorController(props, "DATABASE");

  const changeSource = (value: string) => {
    let next = setPipelineDatasource(editor.definition, "source", value);
    const source = editor.dataSources.find((item) => item.id === value);
    if (source?.database) {
      next = setPipelineDatabaseScope(
        next,
        source.database,
        editor.sinkDatasource?.database || "",
      );
    }
    editor.edit(next);
  };

  const changeSink = (value: string) => {
    let next = setPipelineDatasource(editor.definition, "sink", value);
    const target = editor.dataSources.find((item) => item.id === value);
    if (target?.database) {
      next = setPipelineSinkDatabase(next, target.database);
    }
    editor.edit(next);
  };

  const validateDatabase = () => {
    if (!pipelineDatabaseRoute(editor.definition)) {
      throw new Error("整库同步必须配置来源数据库和目标数据库");
    }
  };

  return (
    <TaskEditorFrame
      editor={editor}
      anchorItems={ANCHORS}
      onSave={(publish) => void editor.save(publish, validateDatabase)}
      onPreview={() => void editor.preview(validateDatabase)}
    >
      <DataSourceSection
        id={editor.task?.id}
        newTaskPath="/data-sync/new/database"
        sourceType={editor.sourceType}
        targetType={editor.sinkType}
        sourceDataSources={editor.dataSources.filter((item) => item.dbType === "MYSQL")}
        targetDataSources={editor.dataSources.filter((item) => item.dbType === "MYSQL")}
        sourceDataSourceId={editor.sourceId}
        targetDataSourceId={editor.sinkId}
        loading={editor.dataSourcesLoading}
        onRefresh={editor.loadDataSources}
        onSourceChange={changeSource}
        onTargetChange={changeSink}
      />

      <ContentSection id="source" title="同步范围">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div className="rounded-sm border border-[var(--yak-components-panel-border)] bg-white px-4 py-4">
            <div className="text-xs text-[var(--yak-components-muted-text)]">
              来源数据库
            </div>
            <div className="mt-1 text-sm font-medium text-[var(--yak-components-field-label)]">
              {editor.sourceDatasource?.database || "请选择来源数据源"}
            </div>
            <div className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
              自动匹配数据库下的全部表，不需要逐表选择。
            </div>
          </div>
          <div className="rounded-sm border border-[var(--yak-components-panel-border)] bg-white px-4 py-4">
            <div className="text-xs text-[var(--yak-components-muted-text)]">
              Flink CDC 表匹配
            </div>
            <div className="mt-1 font-mono text-sm text-[var(--yak-components-field-label)]">
              {editor.sourceDatasource?.database
                ? editor.sourceDatasource.database + ".\\.*"
                : "-"}
            </div>
            <div className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
              新增到来源库中的表也会由整库规则统一匹配。
            </div>
          </div>
        </div>
      </ContentSection>

      <ContentSection id="target" title="目标映射">
        <div className="rounded-sm border border-[var(--yak-components-panel-border)] bg-white px-4 py-4">
          <div className="text-xs text-[var(--yak-components-muted-text)]">
            整库路由
          </div>
          <div className="mt-1 font-mono text-sm font-medium text-[var(--yak-components-field-label)]">
            {editor.sourceDatasource?.database || "-"}.* →{" "}
            {editor.sinkDatasource?.database || "-"}.*
          </div>
          <div className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
            使用 Flink CDC replace-symbol 路由，目标表默认保留来源表名。
          </div>
        </div>
      </ContentSection>
    </TaskEditorFrame>
  );
}
