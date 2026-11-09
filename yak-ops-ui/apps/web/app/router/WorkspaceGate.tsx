import { Button, Input } from "@yak-ops/yak-ui";
import { useState } from "react";
import { Outlet } from "react-router-dom";

import { useWorkspace } from "@/hooks/use-workspace";

import AppLoading from "./AppLoading";

export default function WorkspaceGate() {
  const {
    currentWorkspace,
    loading,
    error,
    createWorkspace,
    refreshWorkspaces,
  } = useWorkspace();
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);

  if (loading) {
    return <AppLoading />;
  }

  if (error) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-[var(--yak-color-hover)] px-4">
        <div className="w-full max-w-sm rounded-xl border border-black/5 bg-white p-6 shadow-sm">
          <h1 className="text-base font-semibold text-[var(--yak-components-panel-text)]">
            工作空间加载失败
          </h1>
          <Button
            className="mt-5 w-full"
            size="sm"
            variant="primary"
            onClick={() => void refreshWorkspaces().catch(() => undefined)}
          >
            重新加载
          </Button>
        </div>
      </div>
    );
  }

  if (currentWorkspace) {
    return <Outlet />;
  }

  const create = async () => {
    const trimmedName = name.trim();
    if (!trimmedName || creating) return;

    setCreating(true);
    try {
      await createWorkspace({ name: trimmedName });
    } catch {
      return;
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[var(--yak-color-hover)] px-4">
      <div className="w-full max-w-sm rounded-xl border border-black/5 bg-white p-6 shadow-sm">
        <img src="/logo.png" alt="Yak Ops" className="h-7 w-auto object-contain" />
        <h1 className="mt-6 text-lg font-semibold text-[var(--yak-components-panel-text)]">
          创建工作空间
        </h1>
        <p className="mt-1 text-xs text-black/45">
          开始使用 Yak Ops 前，请先创建一个工作空间。
        </p>
        <Input
          autoFocus
          aria-label="工作空间名称"
          className="mt-5"
          maxLength={128}
          size="small"
          variant="outlined"
          value={name}
          placeholder="工作空间名称"
          onChange={(event) => setName(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") void create();
          }}
        />
        <Button
          className="mt-4 w-full"
          size="sm"
          variant="primary"
          isPending={creating}
          isDisabled={!name.trim()}
          onClick={() => void create()}
        >
          创建并进入
        </Button>
      </div>
    </div>
  );
}
