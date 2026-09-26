import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import { useWorkspace } from "@/hooks/use-workspace";

import ProductLauncher from "./ProductLauncher";
import ProductSidebar from "./ProductSidebar";
import TopBar from "./TopBar";

export default function AppLayout() {
  const location = useLocation();
  const { currentWorkspace, loading: workspaceLoading } = useWorkspace();
  const [launcherOpen, setLauncherOpen] = useState(false);

  useEffect(() => {
    setLauncherOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!launcherOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setLauncherOpen(false);
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [launcherOpen]);

  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-white text-[#161823]">
      <TopBar
        launcherOpen={launcherOpen}
        onToggleLauncher={() => setLauncherOpen((open) => !open)}
      />

      <div className="flex min-h-0 flex-1">
        <ProductSidebar />

        <main className="min-w-0 flex-1 overflow-y-auto bg-white">
          {workspaceLoading ? (
            <div className="flex h-full items-center justify-center text-sm text-black/45">
              正在加载工作空间…
            </div>
          ) : currentWorkspace ? (
            <div key={currentWorkspace.id} className="h-full">
              <Outlet />
            </div>
          ) : (
            <div className="flex h-full items-center justify-center bg-[#F6F6F6]">
              <div className="text-center">
                <div className="text-sm font-semibold text-[#242731]">暂无工作空间</div>
                <div className="mt-2 text-xs text-[#8b929e]">
                  请从顶部工作空间菜单新建一个工作空间后继续。
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      <ProductLauncher open={launcherOpen} onClose={() => setLauncherOpen(false)} />
    </div>
  );
}
