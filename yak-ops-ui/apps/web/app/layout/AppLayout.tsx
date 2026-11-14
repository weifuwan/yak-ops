import { useMemo } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { useWorkspace } from "@/hooks/use-workspace";

import AppShell from "./AppShell";
import ProductSidebar from "./ProductSidebar";
import type {
  ProductModuleNavigationItem,
  ProductNavigationItem,
} from "./navigation";

type AppLayoutProps = {
  productLabel: string;
  productPath: string;
  navigation?: ProductNavigationItem[];
  moduleNavigation?: ProductModuleNavigationItem[];
};

export default function AppLayout({
  productLabel,
  productPath,
  navigation = [],
  moduleNavigation,
}: AppLayoutProps) {
  const { currentWorkspace } = useWorkspace();
  const location = useLocation();
  const activeModule = useMemo(() => {
    if (!moduleNavigation?.length) return undefined;

    return moduleNavigation
      .filter((item) =>
        item.exact
          ? location.pathname === item.matchPath
          : location.pathname === item.matchPath ||
            location.pathname.startsWith(item.matchPath + "/"),
      )
      .sort((left, right) => right.matchPath.length - left.matchPath.length)[0];
  }, [location.pathname, moduleNavigation]);
  const currentNavigation = activeModule?.navigation ?? navigation;
  const currentLabel = activeModule?.label ?? productLabel;

  return (
    <AppShell
      productLabel={productLabel}
      productPath={productPath}
      moduleNavigation={moduleNavigation}
      activeModuleId={activeModule?.id}
    >
      {currentNavigation.length > 0 ? (
        <ProductSidebar productLabel={currentLabel} navigation={currentNavigation} />
      ) : null}
      <main className="min-w-0 flex-1 overflow-y-auto bg-white">
        <Outlet key={currentWorkspace?.id ?? "no-project"} />
      </main>
    </AppShell>
  );
}
