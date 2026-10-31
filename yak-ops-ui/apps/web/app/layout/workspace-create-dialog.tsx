import { Button, Dialog, DialogContent, DialogTitle, Input, toast } from "@yak-ops/yak-ui";
import { useState } from "react";
import { useWorkspace } from "@/hooks/use-workspace";

export default function WorkspaceCreateDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const { createWorkspace } = useWorkspace();
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);

  const close = () => {
    if (creating) return;
    setName("");
    onClose();
  };
  const create = async () => {
    const trimmed = name.trim();
    if (!trimmed || creating) return;
    setCreating(true);
    try {
      const workspace = await createWorkspace({ name: trimmed });
      if (!workspace) return;
      setName("");
      onClose();
      toast.success("工作空间创建成功");
    } finally {
      setCreating(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(value) => {
        if (!value) close();
      }}
    >
      <DialogContent className="w-[420px]">
        <DialogTitle className="text-base font-semibold">新建工作空间</DialogTitle>
        <div className="mt-5">
          <Input
            autoFocus
            aria-label="工作空间名称"
            maxLength={128}
            value={name}
            placeholder="工作空间名称"
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") void create();
            }}
          />
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <Button disabled={creating} onClick={close}>
            取消
          </Button>
          <Button
            variant="primary"
            loading={creating}
            disabled={!name.trim()}
            onClick={() => void create()}
          >
            创建
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
