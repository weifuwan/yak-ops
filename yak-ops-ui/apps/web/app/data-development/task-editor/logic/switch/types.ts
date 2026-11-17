export type SwitchCase = {
  condition: string;
  nextNode: number | null;
};

export type SwitchLocalParam = {
  prop: string;
  direct?: string;
  type?: string;
  value?: string | null;
  sensitive?: boolean;
  [key: string]: unknown;
};

export type SwitchTaskParams = {
  localParams?: SwitchLocalParam[];
  nextBranch?: number | null;
  switchResult: {
    dependTaskList: SwitchCase[];
    nextNode: number | null;
  };
};
