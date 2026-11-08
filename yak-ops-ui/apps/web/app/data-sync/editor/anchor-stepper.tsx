
import {
  useEffect,
  useState,
  type RefObject,
} from "react";

export interface EditorAnchorItem {
  id: string;
  label: string;
}

interface EditorAnchorStepperProps {
  items: EditorAnchorItem[];
  scrollRootRef?: RefObject<HTMLElement | null>;
}

const STEP_HEIGHT = 64;
const ACTIVATION_OFFSET = 24;

function isScrollRootAtBottom(
  root?: HTMLElement | null,
) {
  if (root) {
    return (
      root.scrollTop + root.clientHeight >=
      root.scrollHeight - 2
    );
  }

  return (
    window.scrollY + window.innerHeight >=
    document.documentElement.scrollHeight - 2
  );
}

export function EditorAnchorStepper({
  items,
  scrollRootRef,
}: EditorAnchorStepperProps) {
  const [activeId, setActiveId] = useState(
    items[0]?.id ?? "",
  );

  useEffect(() => {
    const scrollRoot = scrollRootRef?.current;
    const scrollTarget = scrollRoot ?? window;

    const updateActive = () => {
      if (!items.length) {
        setActiveId("");
        return;
      }

      if (isScrollRootAtBottom(scrollRoot)) {
        setActiveId(items[items.length - 1].id);
        return;
      }

      const rootTop =
        scrollRoot?.getBoundingClientRect().top ?? 0;

      const activationLine =
        rootTop + ACTIVATION_OFFSET;

      let nextActiveId = items[0].id;

      for (const item of items) {
        const section = document.getElementById(
          item.id,
        );

        if (!section) {
          continue;
        }

        if (
          section.getBoundingClientRect().top <=
          activationLine
        ) {
          nextActiveId = item.id;
        } else {
          break;
        }
      }

      setActiveId(nextActiveId);
    };

    updateActive();

    scrollTarget.addEventListener(
      "scroll",
      updateActive,
      { passive: true },
    );

    window.addEventListener("resize", updateActive);

    return () => {
      scrollTarget.removeEventListener(
        "scroll",
        updateActive,
      );
      window.removeEventListener(
        "resize",
        updateActive,
      );
    };
  }, [items, scrollRootRef]);

  const scrollToSection = (id: string) => {
    const section = document.getElementById(id);

    if (!section) {
      return;
    }

    setActiveId(id);

    section.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  };

  return (
    <nav
      aria-label="任务配置导航"
      className="w-[150px]"
    >
      <ol className="m-0 list-none p-0">
        {items.map((item, index) => {
          const active = item.id === activeId;
          const last = index === items.length - 1;

          return (
            <li
              key={item.id}
              className="relative"
              style={{
                minHeight: last
                  ? 24
                  : STEP_HEIGHT,
              }}
            >
              {!last && (
                <span
                  aria-hidden="true"
                  className="
                    absolute
                    left-[3.5px]
                    top-[28px]
                    bottom-[12px]
                    w-px
                    bg-[#E5E7EB]
                  "
                />
              )}

              <button
                type="button"
                aria-current={
                  active ? "step" : undefined
                }
                onClick={() =>
                  scrollToSection(item.id)
                }
                className="
                  group
                  relative
                  flex
                  min-h-6
                  w-full
                  cursor-pointer
                  items-center
                  gap-4
                  border-0
                  bg-transparent
                  p-0
                  text-left
                "
              >
                <span
                  aria-hidden="true"
                  className={[
                    "relative",
                    "z-10",
                    "size-2",
                    "shrink-0",
                    "rounded-full",
                    active
                      ? "bg-[#155EEF]"
                      : "bg-[#8B9098]",
                  ].join(" ")}
                />

                <span
                  className={[
                    "min-w-0",
                    "flex-1",
                    "text-sm",
                    "font-normal",
                    "leading-6",
                    "break-words",
                    "transition-colors",
                    active
                      ? "text-[#155EEF]"
                      : "text-[#8B9098] group-hover:text-[#4E5969]",
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
