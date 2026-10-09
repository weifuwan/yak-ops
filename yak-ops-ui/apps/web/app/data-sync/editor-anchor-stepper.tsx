import { useEffect, useState, type RefObject } from "react";

export interface EditorAnchorItem {
  id: string;
  label: string;
}

interface EditorAnchorStepperProps {
  items: EditorAnchorItem[];
  scrollRootRef?: RefObject<HTMLElement | null>;
}

function isScrollRootAtBottom(root?: HTMLElement | null) {
  if (root) {
    return root.scrollTop + root.clientHeight >= root.scrollHeight - 2;
  }
  return window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2;
}

export function EditorAnchorStepper({ items, scrollRootRef }: EditorAnchorStepperProps) {
  const [activeId, setActiveId] = useState(items[0]?.id || "");

  useEffect(() => {
    const scrollRoot = scrollRootRef?.current;
    const scrollTarget: HTMLElement | Window = scrollRoot || window;

    const updateActive = () => {
      if (items.length === 0) {
        setActiveId("");
        return;
      }

      if (isScrollRootAtBottom(scrollRoot)) {
        setActiveId(items[items.length - 1]?.id || "");
        return;
      }

      const rootTop = scrollRoot?.getBoundingClientRect().top || 0;
      const activationLine = rootTop + 24;
      let nextActiveId = items[0]?.id || "";

      for (const item of items) {
        const section = document.getElementById(item.id);
        if (!section) continue;

        if (section.getBoundingClientRect().top <= activationLine) {
          nextActiveId = item.id;
        } else {
          break;
        }
      }

      setActiveId(nextActiveId);
    };

    updateActive();
    scrollTarget.addEventListener("scroll", updateActive, { passive: true });
    window.addEventListener("resize", updateActive);

    return () => {
      scrollTarget.removeEventListener("scroll", updateActive);
      window.removeEventListener("resize", updateActive);
    };
  }, [items, scrollRootRef]);

  const scrollToSection = (id: string) => {
    const section = document.getElementById(id);
    if (!section) return;

    setActiveId(id);
    section.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <nav
      aria-label="任务配置导航"
      className="rounded-xl border border-[#e6e8eb] bg-white px-4 py-3"
    >
      {items.map((item, index) => {
        const active = item.id === activeId;
        const last = index === items.length - 1;

        return (
          <button
            key={item.id}
            type="button"
            className="group relative flex min-h-12 w-full cursor-pointer items-start gap-3 bg-transparent text-left"
            aria-current={active ? "step" : undefined}
            onClick={() => scrollToSection(item.id)}
          >
            <span className="relative mt-[18px] flex size-2 shrink-0 items-center justify-center">
              {!last ? (
                <span className="absolute left-1/2 top-2 h-[40px] w-px -translate-x-1/2 bg-[#e4e7ec]" />
              ) : null}
              <span
                className={
                  active
                    ? "relative z-10 size-2 rounded-full bg-[var(--yak-color-primary)]"
                    : "relative z-10 size-2 rounded-full bg-[#a4a7ae]"
                }
              />
            </span>
            <span
              className={
                active
                  ? "pt-[13px] text-[13px] font-medium text-[var(--yak-color-primary)]"
                  : "pt-[13px] text-[13px] text-[#98a2b3] transition-colors group-hover:text-[#667085]"
              }
            >
              {item.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
}

export default EditorAnchorStepper;
