import { useEffect, useState } from "react";
import { toast } from "@yak-ops/yak-ui";

import { updateWorkflowDefinitionSettings } from "@/service/workflow/definition";
import { useDataDevelopmentWorkbench } from "../../../../workbench-context";
import { parseGlobalParameters } from "../shared/utils";
import type {
  ExecutionType,
  GlobalParameter,
  WorkflowPropertyForm,
} from "../shared/types";
import type { WorkflowDagRecord } from "@/service/workflow/definition";

interface UseWorkflowPropertiesOptions {
  workflowCode: number;
  definition: WorkflowDagRecord["workflowDefinition"];
  invalidateDefinition: () => Promise<void>;
}

export function useWorkflowProperties({
  workflowCode,
  definition,
  invalidateDefinition,
}: UseWorkflowPropertiesOptions) {
  const { updateTabTitle } = useDataDevelopmentWorkbench();

  const [propertyForm, setPropertyForm] = useState<WorkflowPropertyForm>({
    name: dag.workflowDefinition.name,
    description: dag.workflowDefinition.description ?? "",
    timeout: String(dag.workflowDefinition.timeout ?? 0),
    executionType: (dag.workflowDefinition.executionType ?? "PARALLEL") as ExecutionType,
  });
  const [globalParameters, setGlobalParameters] = useState<GlobalParameter[]>(
    () => parseGlobalParameters(dag.workflowDefinition.globalParams),
  );
  const [savedGlobalParameterCount, setSavedGlobalParameterCount] = useState(
    () => parseGlobalParameters(dag.workflowDefinition.globalParams).length,
  );
  const [propertySaving, setPropertySaving] = useState(false);
  const [globalParametersSaving, setGlobalParametersSaving] = useState(false);

  useEffect(() => {
    setPropertyForm({
      name: definition.name,
      description: definition.description ?? "",
      timeout: String(definition.timeout ?? 0),
      executionType: (definition.executionType ?? "PARALLEL") as ExecutionType,
    });
    const nextParameters = parseGlobalParameters(definition.globalParams);
    setGlobalParameters(nextParameters);
    setSavedGlobalParameterCount(nextParameters.length);
  }, [
    workflowCode,
    definition.version,
    definition.name,
    definition.description,
    definition.timeout,
    definition.executionType,
    definition.globalParams,
  ]);

  const saveProperties = async () => {
    const name = propertyForm.name.trim();
    const timeout = Number(propertyForm.timeout);
    if (!name) {
      toast.error("工作流名称不能为空");
      return;
    }
    if (!Number.isInteger(timeout) || timeout < 0) {
      toast.error("超时时间必须是大于或等于 0 的整数");
      return;
    }

    setPropertySaving(true);
    try {
      await updateWorkflowDefinitionSettings(workflowCode, {
        name,
        description: propertyForm.description,
        timeout,
        executionType: propertyForm.executionType,
      });
      updateTabTitle("workflow:" + workflowCode, name);
      await invalidateDefinition();
      toast.success("工作流属性已保存");
    } catch (error) {
      toast.error("保存工作流属性失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setPropertySaving(false);
    }
  };

  const saveGlobalParameters = async () => {
    if (savedGlobalParameterCount > 0 && globalParameters.length === 0) {
      toast.error("当前后端暂不支持清空全部全局参数");
      return;
    }
    const keys = globalParameters.map((item) => item.prop.trim());
    const originalSensitiveKeys = new Set(
      parseGlobalParameters(definition.globalParams)
        .filter((item) => item.sensitive && item.value === "******")
        .map((item) => item.prop),
    );
    if (
      globalParameters.some(
        (item) =>
          item.sensitive &&
          item.value === "******" &&
          !originalSensitiveKeys.has(item.prop.trim()),
      )
    ) {
      toast.error("新增或重命名敏感参数时，请重新填写实际参数值");
      return;
    }
    if (keys.some((key) => !key)) {
      toast.error("请填写所有全局参数名称");
      return;
    }
    if (new Set(keys).size !== keys.length) {
      toast.error("全局参数名称不能重复");
      return;
    }

    setGlobalParametersSaving(true);
    try {
      await updateWorkflowDefinitionSettings(workflowCode, {
        globalParams: JSON.stringify(
          globalParameters.map((item) => ({ ...item, prop: item.prop.trim() })),
        ),
      });
      setSavedGlobalParameterCount(globalParameters.length);
      await invalidateDefinition();
      toast.success("全局参数已保存");
    } catch (error) {
      toast.error("保存全局参数失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setGlobalParametersSaving(false);
    }
  };

  const updateGlobalParameter = (
    index: number,
    patch: Partial<GlobalParameter>,
  ) => {
    setGlobalParameters((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    );
  };

  const resetProperties = () => {
    setPropertyForm({
      name: definition.name,
      description: definition.description ?? "",
      timeout: String(definition.timeout ?? 0),
      executionType: (definition.executionType ?? "PARALLEL") as ExecutionType,
    });
  };

  return {
    propertyForm,
    setPropertyForm,
    globalParameters,
    setGlobalParameters,
    savedGlobalParameterCount,
    propertySaving,
    globalParametersSaving,
    saveProperties,
    saveGlobalParameters,
    updateGlobalParameter,
    resetProperties,
  };
}
