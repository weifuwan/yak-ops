import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import ProductLauncher from "./ProductLauncher";
import ProductSidebar from "./ProductSidebar";
import TopBar from "./TopBar";

export default function AppLayout() {
  const location = useLocation();
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
          <Outlet />
        </main>
      </div>

      <ProductLauncher open={launcherOpen} onClose={() => setLauncherOpen(false)} />
    </div>
  );
}
