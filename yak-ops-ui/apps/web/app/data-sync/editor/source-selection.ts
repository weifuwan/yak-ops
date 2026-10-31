import type { DataSourceCatalogTable } from "@/service/datasource";
import type { EditorForm } from "./types";

/** Source selection resets dependent write intent atomically; it never creates physical options. */
function clearTargetWriteIdentity(form: EditorForm): EditorForm["sink"] {
  return {
    ...form.sink,
    targetPrimaryKeys: [],
  };
}

export function selectSourceSchema(form: EditorForm, schema: string): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      schema,
      table: "",
    },
    sink: clearTargetWriteIdentity(form),
  };
}

export function selectSourceTable(form: EditorForm, table: DataSourceCatalogTable): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      database: form.source.database || table.database || "",
      schema: form.source.schema || table.schema || "",
      table: table.name,
    },
    sink: clearTargetWriteIdentity(form),
  };
}
