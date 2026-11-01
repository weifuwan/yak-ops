import { useState, type ComponentProps } from "react";
import { ReplicationTableSelection } from "./replication-table-selection";
import { ReplicationMappingTable, type MappingDialog } from "./replication-mapping-table";
import { useResolveTableSelection } from "./use-resolve-table-selection";

export function DatabaseReplicationBody({
  selection,
  mapping,
}: {
  selection: Omit<ComponentProps<typeof ReplicationTableSelection>, "resolution" | "disabled">;
  mapping: Omit<
    ComponentProps<typeof ReplicationMappingTable>,
    "dialog" | "onDialogChange" | "disabled"
  >;
}) {
  const [dialog, setDialog] = useState<MappingDialog>();
  const resolution = useResolveTableSelection(
    selection.form.source.dataSourceId,
    selection.capabilities?.maxTables ?? 0,
    JSON.stringify([selection.form.selection, selection.form.mappingTemplate]),
    mapping.mappings,
  );
  return (
    <>
      <ReplicationTableSelection
        {...selection}
        resolution={resolution}
        disabled={Boolean(dialog)}
      />
      <ReplicationMappingTable
        {...mapping}
        dialog={dialog}
        onDialogChange={setDialog}
        disabled={resolution.loading}
      />
    </>
  );
}
