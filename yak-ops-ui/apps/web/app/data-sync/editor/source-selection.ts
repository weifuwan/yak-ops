import type { DataSourceCatalogTable } from "@/service/datasource";
import type { EditorForm } from "./types";

/** Source selection resets dependent write intent atomically; it never creates physical options. */
function clearTargetWriteIdentity(form: EditorForm, continuous: boolean): EditorForm["sink"] {
  return {
    ...form.sink,
    targetPrimaryKeys: [],
    writeMode: continuous ? "UPSERT" : form.sink.writeMode,
  };
}

export function selectSourceSchema(
  form: EditorForm,
  schema: string,
  continuous: boolean,
): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      schema,
      table: "",
      sourceHasPrimaryKey: undefined,
      chunkKeyColumn: "",
    },
    sink: clearTargetWriteIdentity(form, continuous),
  };
}

export function selectSourceTable(
  form: EditorForm,
  table: DataSourceCatalogTable,
  continuous: boolean,
): EditorForm {
  return {
    ...form,
    source: {
      ...form.source,
      database: form.source.database || table.database || "",
      schema: form.source.schema || table.schema || "",
      table: table.name,
      sourceHasPrimaryKey: undefined,
      chunkKeyColumn: "",
    },
    sink: clearTargetWriteIdentity(form, continuous),
  };
}

/** Raw keys select defaults only when no transform can change the sink write identity. */
export function setSourcePrimaryKeys(
  form: EditorForm,
  hasPrimaryKey: boolean,
  continuous: boolean,
  hasTransforms = false,
): EditorForm {
  if (form.source.sourceHasPrimaryKey === hasPrimaryKey) return form;
  const retract = !hasPrimaryKey && form.sink.targetPrimaryKeys.length === 0;
  return {
    ...form,
    source: { ...form.source, sourceHasPrimaryKey: hasPrimaryKey },
    sink: continuous
      ? {
          ...form.sink,
          writeMode: hasTransforms ? form.sink.writeMode : retract ? "RETRACT" : "UPSERT",
          schemaChangesEnabled: hasPrimaryKey ? form.sink.schemaChangesEnabled : false,
          allowDropColumns: hasPrimaryKey ? form.sink.allowDropColumns : false,
        }
      : form.sink,
  };
}
