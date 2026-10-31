import { useEffect, useRef } from "react";
import { toast } from "@yak-ops/yak-ui";
import {
  getDataSyncTask,
  type DataSyncTaskRecord,
  type TaskParameterVersion,
} from "@/service/data-sync";
import { restoreParameterVersion } from "@/service/data-sync/versions";
import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import { createEditorForm } from "./form-state";
import type { EditorConfiguration } from "./configuration";
import type { EditorForm } from "./types";

export interface EditorConfirmation {
  title: string;
  message: string;
  apply: () => void;
}

function configuration(
  task: DataSyncTaskRecord,
  connections: DataSourceRecord[],
): EditorConfiguration {
  const sourceType = connections[0].dbType;
  const targetType = connections[1].dbType;
  if (!sourceType || !targetType) throw new Error("数据源类型不可用");
  const draft = {
    name: task.name,
    sourceType,
    targetType,
    sourceConnector: task.sourceConnector,
    sinkConnector: task.sinkConnector,
    sourceProducesChangelog: task.sourceProducesChangelog,
    productType: task.productType,
    steps: task.steps,
  };
  return { draft, form: createEditorForm(draft, task), mappings: task.tableMappings };
}

/** Resolve referenced datasources before an explicit replacement of the page's edited parameters. */
export function useDefinitionRestoration({
  task,
  form,
  closeDrawer,
  confirm,
  apply,
  replaceRemote,
}: {
  task?: DataSyncTaskRecord;
  form: EditorForm;
  closeDrawer: () => void;
  confirm: (value: EditorConfirmation) => void;
  apply: (value: EditorConfiguration, connections: DataSourceRecord[]) => void;
  replaceRemote: (
    task: DataSyncTaskRecord,
    value: EditorConfiguration,
    connections: DataSourceRecord[],
  ) => void;
}) {
  const session = useRef<AbortController>();
  const latest = useRef({ form, apply, replaceRemote });
  latest.current = { form, apply, replaceRemote };
  useEffect(() => {
    const controller = new AbortController();
    session.current = controller;
    return () => {
      session.current?.abort();
      session.current = undefined;
    };
  }, []);
  const begin = () => {
    session.current?.abort();
    const controller = new AbortController();
    session.current = controller;
    return controller.signal;
  };
  const load = async (record: DataSyncTaskRecord, signal: AbortSignal) =>
    Promise.all([
      getDataSource(record.sourceDataSourceId, signal),
      getDataSource(record.targetDataSourceId, signal),
    ]);
  const restore = async (version: TaskParameterVersion) => {
    if (!task || !session.current) return;
    const signal = begin();
    try {
      const restored = restoreParameterVersion(task, version);
      const connections = await load(restored, signal);
      if (signal.aborted) return;
      const next = configuration(restored, connections);
      closeDrawer();
      confirm({
        title: `还原参数版本 V${version.version}`,
        message: "历史参数将替换当前页面的参数修改，确认后仍需保存和发布。",
        apply: () => {
          if (signal.aborted) return;
          const current = latest.current;
          current.apply(
            {
              ...next,
              draft: { ...next.draft, name: current.form.name },
              form: {
                ...next.form,
                name: current.form.name,
                remark: current.form.remark,
                debug: current.form.debug,
              },
            },
            connections,
          );
        },
      });
    } catch (failure) {
      if (!signal.aborted)
        toast.error(failure instanceof Error ? failure.message : "历史参数还原失败");
    }
  };
  const reloadRemote = async () => {
    if (!task || !session.current) return;
    const signal = begin();
    try {
      const remote = await getDataSyncTask(task.id, signal);
      const connections = await load(remote, signal);
      if (signal.aborted) return;
      const next = configuration(remote, connections);
      confirm({
        title: "加载远端配置",
        message: "此操作将替换当前未保存的任务修改。",
        apply: () => {
          if (!signal.aborted) latest.current.replaceRemote(remote, next, connections);
        },
      });
    } catch (failure) {
      if (!signal.aborted)
        toast.error(failure instanceof Error ? failure.message : "远端配置加载失败");
    }
  };
  return { restore, reloadRemote, cancel: () => session.current?.abort() };
}
