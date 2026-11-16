import type { ModelingRecord } from "@/service/data-modeling/shared/types";

export interface DictionaryStandardRecord extends ModelingRecord {
  enShortName?: string;
  enName?: string;
  cnName?: string;
  flag?: number;
  createBy?: string;
  updateBy?: string;
}
export interface DictionaryFormValues {
  enShortName: string;
  enName: string;
  cnName: string;
}
export const DICTIONARY_PAGE_SIZE = 10;
