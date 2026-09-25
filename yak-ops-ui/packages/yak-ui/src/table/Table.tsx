import type { TableProps } from "./interface";
import { InternalTable } from "./InternalTable";

export function Table<RecordType extends object>(props: TableProps<RecordType>) {
  return <InternalTable {...props} />;
}
