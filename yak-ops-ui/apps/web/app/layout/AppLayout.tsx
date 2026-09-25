import { Outlet } from "react-router-dom";

import ProductSidebar from "./ProductSidebar";
import TopBar from "./TopBar";

export default function AppLayout() {
  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-white text-[#161823]">
      <TopBar />

      <div className="flex min-h-0 flex-1">
        <ProductSidebar />

        <main className="min-w-0 flex-1 overflow-y-auto bg-white">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
