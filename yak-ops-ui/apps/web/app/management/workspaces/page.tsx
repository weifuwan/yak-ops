import { useState } from "react";
import {
  Button, Dialog, DialogContent, DialogDescription, DialogTitle, PageHeader, toast,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { deleteWorkspace, type WorkspaceRecord } from "@/service/workspace";
import { WorkspaceTable } from "./workspace-table";
import WorkspaceForm from "./workspace-form";
import { MemberDialog } from "./members/member-dialog";

export function WorkspaceManagementPage() {
  const { currentUser } = useAuth();
  const { workspaces, loading, error, refreshWorkspaces } = useWorkspace();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<WorkspaceRecord>();
  const [members, setMembers] = useState<WorkspaceRecord>();
  const [pendingDelete, setPendingDelete] = useState<WorkspaceRecord>();
  const [deleting, setDeleting] = useState(false);
  const openCreate = () => { setEditing(undefined); setFormOpen(true); };
  const openEdit = (record: WorkspaceRecord) => { setEditing(record); setFormOpen(true); };
  const performDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    try {
      await deleteWorkspace(pendingDelete.id);
      toast.success("工作空间已删除");
      setPendingDelete(undefined);
      await refreshWorkspaces();
    } catch {
      // Project deletion or refresh failed; the HTTP layer reports the problem.
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="工作空间管理" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 items-center">
              <Button size="small" variant="primary" onClick={openCreate}>
                <Plus size={14} /> 新建工作空间
              </Button>
            </section>
            <section className="mt-4 min-h-0 flex-1">
              {error ? (
                <div role="alert" className="mb-3 flex items-center gap-3 text-sm">
                  工作空间加载失败
                  <Button size="small" onClick={() => void refreshWorkspaces().catch(() => undefined)}>重试</Button>
                </div>
              ) : null}
              <WorkspaceTable records={workspaces} loading={loading}
                currentUserId={currentUser?.id} isAdmin={currentUser?.userType === "ADMIN_USER"}
                onMembers={setMembers} onEdit={openEdit} onDelete={setPendingDelete} />
            </section>
          </div>
        </div>
      </div>
      <WorkspaceForm
        open={formOpen} record={editing} onClose={() => setFormOpen(false)}
        onSaved={() => {
          toast.success(editing ? "工作空间保存成功" : "工作空间创建成功");
          void refreshWorkspaces().catch(() => undefined);
        }}
      />
      {members ? <MemberDialog key={members.id} workspace={members} onClose={() => setMembers(undefined)} /> : null}
      <Dialog open={Boolean(pendingDelete)} onOpenChange={(open) => {
        if (!open && !deleting) setPendingDelete(undefined);
      }}>
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">删除工作空间</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除“{pendingDelete?.name || "-"}”吗？存在工作流的项目需要先清理工作流。
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button size="small" disabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
            <Button size="small" variant="danger" loading={deleting}
              onClick={() => void performDelete()}>删除</Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
