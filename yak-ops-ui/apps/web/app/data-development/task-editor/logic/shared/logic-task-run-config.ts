import type { ReactNode } from "react";

import type {
  WorkflowTaskCommonSettingsPatch,
  WorkflowTaskRecord,
} from "@/service/workflow/definition";

export type LogicTaskRunConfigController = {
  isDirty: boolean;
  content: ReactNode;
  getPatch: () => WorkflowTaskCommonSettingsPatch;
  getPreviewPatch: () => Record<string, unknown>;
  validate: () => string | undefined;
  markSaved: () => void;
  refreshFromTask: (task: WorkflowTaskRecord) => void;
};
