import {
  useEffect,
  useState,
  type PropsWithChildren,
} from "react";
import { useLocation } from "react-router-dom";

import "./app-layout.css";

import ProductLauncher from "./ProductLauncher";
import TopBar from "./TopBar";

type AppShellProps = PropsWithChildren<{
  productLabel: string;
  productPath: string;
}>;

export default function AppShell({
  productLabel,
  productPath,
  children,
}: AppShellProps) {
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
    <div className="yak-app-shell-enter flex h-dvh flex-col overflow-hidden bg-white text-[#161823]">
      <TopBar
        launcherOpen={launcherOpen}
        onToggleLauncher={() => setLauncherOpen((open) => !open)}
        productLabel={productLabel}
        productPath={productPath}
      />

      <div className="flex min-h-0 flex-1">{children}</div>

      <ProductLauncher
        open={launcherOpen}
        onClose={() => setLauncherOpen(false)}
      />
    </div>
  );
}
