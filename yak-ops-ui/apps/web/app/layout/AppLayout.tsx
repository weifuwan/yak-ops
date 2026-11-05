import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";

import ProductLauncher from "./ProductLauncher";
import ProductSidebar from "./ProductSidebar";
import TopBar from "./TopBar";
import type { ProductNavigationItem } from "./navigation";

type AppLayoutProps = {
  productLabel: string;
  productPath: string;
  navigation: ProductNavigationItem[];
};

export default function AppLayout({ productLabel, productPath, navigation }: AppLayoutProps) {
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
        productLabel={productLabel}
        productPath={productPath}
      />

      <div className="flex min-h-0 flex-1">
        <ProductSidebar productLabel={productLabel} navigation={navigation} />
        <main className="min-w-0 flex-1 overflow-y-auto bg-white">
          <Outlet />
        </main>
      </div>

      <ProductLauncher open={launcherOpen} onClose={() => setLauncherOpen(false)} />
    </div>
  );
}
