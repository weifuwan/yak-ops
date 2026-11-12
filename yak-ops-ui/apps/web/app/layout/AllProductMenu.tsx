import { Star } from "lucide-react";
import { Link } from "react-router-dom";

import { ALL_PRODUCT_GROUPS } from "./navigation";

type AllProductMenuProps = {
  open: boolean;
  interactive: boolean;
  favoriteProductIds: ReadonlySet<string>;
  favoriteMutatingIds: ReadonlySet<string>;
  onNavigate: () => void;
  onToggleFavorite: (productId: string) => void;
};

export default function AllProductMenu({
  open,
  interactive,
  favoriteProductIds,
  favoriteMutatingIds,
  onNavigate,
  onToggleFavorite,
}: AllProductMenuProps) {
  return (
    <section
      id="all-product-menu"
      aria-label="全部产品二级菜单"
      aria-hidden={!open || !interactive}
      className={[
        "absolute inset-y-0 left-full z-0 w-[765px] max-w-[calc(100vw-220px)] overflow-hidden bg-[#1c1e21] text-xs text-[#cbced3]",
        "transform-gpu transition-transform duration-[240ms] motion-reduce:transition-none",
        open ? "translate-x-0 ease-out" : "-translate-x-full ease-in-out",
        open && interactive ? "pointer-events-auto" : "pointer-events-none",
      ].join(" ")}
    >
      <div className="h-full w-[765px] max-w-[calc(100vw-220px)] overflow-y-auto">
        <div className="min-h-full px-12 pt-4">
          {ALL_PRODUCT_GROUPS.map((group) => (
            <section key={group.id} className="flex min-h-12 border-b border-[#242629] py-2">
              <h3 className="m-0 w-40 shrink-0 py-2 text-xs font-normal leading-8 text-[#f4f4f4]">
                {group.label}
              </h3>

              <div className="flex min-w-0 flex-1 flex-wrap content-start gap-x-5">
                {group.products.map((product) => {
                  const Icon = product.icon;
                  const favorite = favoriteProductIds.has(product.id);
                  const mutating = favoriteMutatingIds.has(product.id);

                  return (
                    <div
                      key={product.id}
                      className="group my-0.5 flex h-8 w-60 items-center rounded px-2 text-[#cbced3] transition-colors hover:bg-[#282b2e] hover:text-[#f4f4f4]"
                    >
                      <Link
                        to={product.path}
                        target="_blank"
                        rel="noopener noreferrer"
                        tabIndex={open && interactive ? 0 : -1}
                        className="flex h-full min-w-0 flex-1 cursor-pointer items-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                        onClick={onNavigate}
                      >
                        <Icon
                          className="h-3.5 w-3.5 shrink-0 text-[#5d6064] transition-colors group-hover:text-[#f4f4f4]"
                          strokeWidth={1.8}
                        />
                        <span className="ml-2 min-w-0 flex-1 truncate">{product.label}</span>
                      </Link>

                      <button
                        type="button"
                        aria-label={favorite ? "取消收藏" + product.label : "收藏" + product.label}
                        aria-pressed={favorite}
                        disabled={mutating}
                        tabIndex={open && interactive ? 0 : -1}
                        className={[
                          "ml-1 flex h-6 w-6 shrink-0 cursor-pointer items-center justify-center rounded transition-[background-color,color,opacity]",
                          "hover:bg-white/8 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20 disabled:cursor-default disabled:opacity-45",
                          favorite
                            ? "text-[#f5a623] opacity-100"
                            : "text-white/40 opacity-0 group-hover:opacity-100 focus-visible:opacity-100",
                        ].join(" ")}
                        onClick={() => onToggleFavorite(product.id)}
                      >
                        <Star
                          className="h-3.5 w-3.5"
                          fill={favorite ? "currentColor" : "none"}
                          strokeWidth={1.8}
                        />
                      </button>
                    </div>
                  );
                })}
              </div>
            </section>
          ))}
        </div>
      </div>
    </section>
  );
}
