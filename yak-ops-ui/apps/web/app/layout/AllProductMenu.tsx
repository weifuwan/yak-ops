import { Link } from "react-router-dom";

import { ALL_PRODUCT_GROUPS } from "./navigation";

type AllProductMenuProps = {
  open: boolean;
  onNavigate: () => void;
};

export default function AllProductMenu({ open, onNavigate }: AllProductMenuProps) {
  return (
    <section
      id="all-product-menu"
      aria-label="全部产品二级菜单"
      aria-hidden={!open}
      className={[
        "fixed bottom-0 left-[220px] top-10 z-30 w-[765px] max-w-[calc(100vw-220px)] overflow-y-auto bg-[#1c1e21] text-xs text-[#cbced3]",
        "transform-gpu transition-transform duration-[240ms] ease-in-out motion-reduce:transition-none",
        open ? "translate-x-0" : "-translate-x-[765px] pointer-events-none",
      ].join(" ")}
    >
      <div className="min-h-full px-12 pt-4">
        {ALL_PRODUCT_GROUPS.map((group) => (
          <section key={group.id} className="flex min-h-12 border-b border-[#242629] py-2">
            <h3 className="m-0 w-40 shrink-0 py-2 text-xs font-normal leading-8 text-[#f4f4f4]">
              {group.label}
            </h3>

            <div className="flex min-w-0 flex-1 flex-wrap content-start gap-x-5">
              {group.products.map((product) => {
                const Icon = product.icon;

                return (
                  <Link
                    key={product.id}
                    to={product.path}
                    tabIndex={open ? 0 : -1}
                    className="group my-0.5 flex h-8 w-60 cursor-pointer items-center rounded px-2 text-[#cbced3] transition-colors hover:bg-[#282b2e] hover:text-[#f4f4f4] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/20"
                    onClick={onNavigate}
                  >
                    <Icon
                      className="h-3.5 w-3.5 shrink-0 text-[#5d6064] transition-colors group-hover:text-[#f4f4f4]"
                      strokeWidth={1.8}
                    />
                    <span className="ml-2 min-w-0 flex-1 truncate">{product.label}</span>
                  </Link>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </section>
  );
}
