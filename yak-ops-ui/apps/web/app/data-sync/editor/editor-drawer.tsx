import { Button, Drawer, DrawerBody, DrawerContent, DrawerTitle } from "@yak-ops/yak-ui";
import { X } from "lucide-react";
import type { ReactNode } from "react";

export function EditorDrawer({
  title,
  width = 760,
  children,
  footer,
  onClose,
}: {
  title: string;
  width?: number;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
}) {
  return (
    <Drawer
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      disablePointerDismissal
    >
      <DrawerContent width={width} data-base-ui-swipe-ignore="">
        <header className="flex shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-5 py-3">
          <DrawerTitle className="text-base font-semibold">{title}</DrawerTitle>
          <Button variant="ghost" size="small" aria-label={`关闭${title}`} onClick={onClose}>
            <X size={16} />
          </Button>
        </header>
        <DrawerBody>{children}</DrawerBody>
        <footer className="flex shrink-0 items-center gap-2 border-t border-[var(--yak-components-panel-border)] px-5 py-3">
          {footer || (
            <Button size="small" onClick={onClose}>
              关闭
            </Button>
          )}
        </footer>
      </DrawerContent>
    </Drawer>
  );
}
