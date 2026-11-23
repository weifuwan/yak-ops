import { AlertRecordsPanel } from "@/app/management/alerts/records-panel";
import { OperationsPageShell } from "../components/operations-page-shell";

export function OperationsAlertsPage() {
  return (
    <OperationsPageShell title="任务告警">
      <div className="min-h-0 flex-1">
        <AlertRecordsPanel />
      </div>
    </OperationsPageShell>
  );
}
