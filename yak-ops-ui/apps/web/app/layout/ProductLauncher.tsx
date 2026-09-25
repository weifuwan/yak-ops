import { ChevronRight, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { PRODUCT_GROUPS } from "./navigation";

type ProductLauncherProps = {
  open: boolean;
  onClose: () => void;
};

export default function ProductLauncher({ open, onClose }: ProductLauncherProps) {
  const [activeGroupId, setActiveGroupId] = useState(PRODUCT_GROUPS[0]?.id ?? "");
  const activeGroup =
    PRODUCT_GROUPS.find((group) => group.id === activeGroupId) ?? PRODUCT_GROUPS[0];

  if (!open || !activeGroup) return null;

  return (
    <div
      id="global-product-launcher"
      className="fixed inset-x-0 bottom-0 top-10 z-40 flex"
      aria-label="全局产品菜单"
    >
      <div className="flex w-[760px] max-w-[calc(100vw-32px)] shadow-[12px_18px_48px_rgba(0,0,0,0.28)]">
        <aside className="flex w-48 shrink-0 flex-col bg-[#171a1e] text-white">
          <div className="flex h-12 items-center border-b border-white/8 px-4 text-sm font-semibold">
            全部产品
          </div>

          <nav className="space-y-1 p-2" aria-label="产品分类">
            {PRODUCT_GROUPS.map((group) => {
              const active = group.id === activeGroup.id;
              return (
                <button
                  key={group.id}
                  type="button"
                  className={[
                    "flex h-10 w-full items-center justify-between rounded-md px-3 text-left text-sm transition-colors",
                    active
                      ? "bg-white/10 font-medium text-white"
                      : "text-white/62 hover:bg-white/6 hover:text-white",
                  ].join(" ")}
                  onClick={() => setActiveGroupId(group.id)}
                >
                  <span>{group.label}</span>
                  <ChevronRight
                    className={active ? "h-4 w-4 text-white/65" : "h-4 w-4 text-white/25"}
                  />
                </button>
              );
            })}
          </nav>
        </aside>

        <section className="min-w-0 flex-1 overflow-y-auto bg-[#1d2126] px-8 py-6 text-white">
          <div className="flex items-center justify-between">
            <h2 className="m-0 text-base font-semibold">{activeGroup.label}</h2>
            <button
              type="button"
              aria-label="关闭产品菜单"
              className="flex h-8 w-8 items-center justify-center rounded-md text-white/45 transition-colors hover:bg-white/8 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
              onClick={onClose}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="mt-5 grid grid-cols-2 gap-3 max-md:grid-cols-1">
            {activeGroup.items.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className="group flex min-h-20 items-center gap-3 rounded-lg border border-white/8 bg-white/[0.025] px-4 py-3 transition-colors hover:border-white/14 hover:bg-white/[0.055]"
                  onClick={onClose}
                >
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/7 text-white/72 group-hover:text-white">
                    <Icon className="h-[18px] w-[18px]" strokeWidth={1.8} />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-medium text-white/92">{item.label}</span>
                    <span className="mt-1 block text-xs text-white/38">进入{item.label}</span>
                  </span>
                </Link>
              );
            })}
          </div>
        </section>
      </div>

      <button
        type="button"
        aria-label="关闭产品菜单"
        tabIndex={-1}
        className="min-w-0 flex-1 cursor-default bg-black/25"
        onClick={onClose}
      />
    </div>
  );
}
