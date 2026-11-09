import { Outlet } from "react-router-dom";
import { useWorkspace } from "@/hooks/use-workspace";

import AppShell from "./AppShell";
import ProductSidebar from "./ProductSidebar";
import type { ProductNavigationItem } from "./navigation";

type AppLayoutProps = {
  productLabel: string;
  productPath: string;
  navigation: ProductNavigationItem[];
};

export default function AppLayout({
  productLabel,
  productPath,
  navigation,
}: AppLayoutProps) {
  const { currentWorkspace } = useWorkspace();

  return (
    <AppShell productLabel={productLabel} productPath={productPath}>
      <ProductSidebar productLabel={productLabel} navigation={navigation} />
      <main className="min-w-0 flex-1 overflow-y-auto bg-white">
        <Outlet key={currentWorkspace?.id ?? "no-project"} />
      </main>
    </AppShell>
  );
}
