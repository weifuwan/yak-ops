import type { DataSourceId } from "./types";

export const dataSourceRecordKey = (id?: DataSourceId) => String(id ?? "");
