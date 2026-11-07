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

export function EditorAnchorStepper({
  items,
  scrollRootRef,
}: EditorAnchorStepperProps) {
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

    scrollTarget.addEventListener("scroll", updateActive, {
      passive: true,
    });
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
    section.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  return (
    <nav
      aria-label="任务配置导航"
      className="w-full max-w-[160px]"
    >
      <ol className="m-0 list-none p-0">
        {items.map((item, index) => {
          const active = item.id === activeId;
          const last = index === items.length - 1;

          return (
            <li
              key={item.id}
              className="relative min-h-12 w-auto"
            >
              <button
                type="button"
                aria-current={active ? "step" : undefined}
                onClick={() => scrollToSection(item.id)}
                className="group flex w-full cursor-pointer items-start bg-transparent p-0 text-left"
              >
                <span className="relative flex w-6 shrink-0 flex-col items-center">
                  <span className="flex h-6 items-center justify-center">
                    <span
                      className={[
                        "relative z-10 size-2 rounded-full",
                        active
                          ? "bg-[#0033CC]"
                          : "bg-[#72767C]",
                      ].join(" ")}
                    />
                  </span>

                  {!last ? (
                    <span className="h-6 w-px bg-[#E6E7EB]" />
                  ) : null}
                </span>

                <span
                  className={[
                    "ml-2 max-w-[120px]",
                    "overflow-hidden text-ellipsis",
                    "whitespace-normal break-words",
                    "text-sm font-normal leading-6",
                    active
                      ? "text-[#0033CC]"
                      : "text-[#72767C] transition-colors group-hover:text-[#333333]",
                  ].join(" ")}
                >
                  {item.label}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

export default EditorAnchorStepper;