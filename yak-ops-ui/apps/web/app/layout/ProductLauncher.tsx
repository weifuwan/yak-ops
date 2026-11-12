import { ChevronRight, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import AllProductMenu from "./AllProductMenu";
import { useProductFavorites } from "./use-product-favorites";

const FIRST_LEVEL_CLOSE_DELAY_MS = 36;

type ProductLauncherProps = { open: boolean; onClose: () => void };

export default function ProductLauncher({ open, onClose }: ProductLauncherProps) {
  const [allProductsOpen, setAllProductsOpen] = useState(false);
  const [firstLevelVisible, setFirstLevelVisible] = useState(open);
  const { favorites, favoriteProductIds, mutatingIds, loading, error, retry, toggleFavorite } =
    useProductFavorites(open);

  useEffect(() => {
    if (open) {
      setFirstLevelVisible(true);
      return;
    }
    setAllProductsOpen(false);
    const timer = window.setTimeout(() => setFirstLevelVisible(false), FIRST_LEVEL_CLOSE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [open]);

  const secondLevelOpen = open && allProductsOpen;

  return (
    <>
      <button
        type="button"
        aria-label="关闭产品菜单"
        tabIndex={-1}
        className={[
          "fixed inset-x-0 bottom-0 top-10 z-20 cursor-default border-0 bg-transparent p-0",
          open ? "pointer-events-auto" : "pointer-events-none",
        ].join(" ")}
        onClick={onClose}
      />

      <div
        className={[
          "fixed bottom-0 left-0 top-10 z-40 w-[220px] transform-gpu transition-transform ease-in-out motion-reduce:transition-none",
          open ? "duration-300" : "duration-[220ms]",
          firstLevelVisible ? "translate-x-0" : "-translate-x-[220px]",
          open ? "" : "pointer-events-none",
        ].join(" ")}
      >
        <aside
          id="global-product-launcher"
          aria-label="全局产品一级菜单"
          aria-hidden={!open}
          className={[
            "user-menu flex h-full w-[220px] flex-col bg-[#15181c] text-xs text-white",
            "box-border",
            firstLevelVisible ? "user-menu-active" : "",
          ].join(" ")}
        >
          <button
            type="button"
            aria-expanded={secondLevelOpen}
            aria-controls="all-product-menu"
            tabIndex={open ? 0 : -1}
            className={[
              "view-all mb-[7px] flex h-10 w-full shrink-0 cursor-pointer items-center border-0 px-0 text-left text-[#d3d3d3] transition-colors",
              "hover:bg-[#282b2e] hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-white/20",
              secondLevelOpen ? "bg-[#1c1e21] text-white" : "bg-transparent",
            ].join(" ")}
            onClick={() => setAllProductsOpen((value) => !value)}
          >
            <span className="text ml-[14px] min-w-0 flex-1 truncate">全部产品</span>
            <span className="right mr-3 flex shrink-0 items-center">
              <ChevronRight
                className={secondLevelOpen ? "h-3.5 w-3.5 text-white" : "h-3.5 w-3.5 text-white/65"}
                strokeWidth={1.8}
              />
            </span>
          </button>

          <div className="item-list min-h-0 flex-1 overflow-y-auto px-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {favorites.length > 0 ? (
              favorites.map((item) => {
                const Icon = item.icon;
                const mutating = mutatingIds.has(item.id);

                return (
                  <div
                    key={item.id}
                    className="item group my-0.5 flex h-8 w-full items-center rounded px-1.5 text-[#cbced3] transition-colors hover:bg-[#282b2e] hover:text-white"
                  >
                    <Link
                      to={item.path}
                      target="_blank"
                      rel="noopener noreferrer"
                      tabIndex={open ? 0 : -1}
                      className="flex h-full min-w-0 flex-1 cursor-pointer items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                      onClick={onClose}
                    >
                      <Icon
                        className="h-3.5 w-3.5 shrink-0 text-[#5d6064] transition-colors group-hover:text-white"
                        strokeWidth={1.8}
                      />
                      <span className="product-name ml-2 min-w-0 flex-1 truncate">
                        {item.label}
                      </span>
                    </Link>

                    <button
                      type="button"
                      aria-label={`取消收藏${item.label}`}
                      disabled={mutating}
                      tabIndex={open ? 0 : -1}
                      className={[
                        "ml-1 flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded text-white/40 opacity-0",
                        "transition-[background-color,color,opacity] hover:bg-white/8 hover:text-white group-hover:opacity-100",
                        "focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20",
                        "disabled:cursor-default disabled:opacity-45",
                      ].join(" ")}
                      onClick={() => toggleFavorite(item.id)}
                    >
                      <X className="h-3.5 w-3.5" strokeWidth={1.8} />
                    </button>
                  </div>
                );
              })
            ) : (
              <div className="px-2 py-3 text-[11px] leading-5 text-white/35">
                {loading ? (
                  "正在加载常用产品…"
                ) : error ? (
                  <button
                    type="button"
                    className="cursor-pointer text-white/80 hover:text-white"
                    onClick={retry}
                  >
                    常用产品加载失败，重试
                  </button>
                ) : (
                  "暂无常用产品，可在全部产品中标星添加"
                )}
              </div>
            )}
          </div>
        </aside>

        <AllProductMenu
          open={secondLevelOpen}
          favoriteProductIds={favoriteProductIds}
          favoriteMutatingIds={mutatingIds}
          onNavigate={onClose}
          onToggleFavorite={toggleFavorite}
        />
      </div>
    </>
  );
}
