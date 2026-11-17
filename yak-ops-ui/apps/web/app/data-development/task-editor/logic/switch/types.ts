export type SwitchCase = {
  condition: string;
  nextNode: number | null;
};

export type SwitchTaskParams = {
  localParams?: unknown[];
  nextBranch?: number | null;
  switchResult: {
    dependTaskList: SwitchCase[];
    nextNode: number | null;
  };
};
