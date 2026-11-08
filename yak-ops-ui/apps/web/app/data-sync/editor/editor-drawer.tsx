import { Button, Drawer, DrawerBody, DrawerContent, DrawerTitle } from "@yak-ops/yak-ui";
import { X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";

export function EditorDrawer({
  title,
  width = 760,
  open,
  children,
  footer,
  onClose,
  onClosed,
}: {
  title: string;
  width?: number;
  open: boolean;
  children: ReactNode;
  footer?: ReactNode;
  onClose: () => void;
  onClosed: () => void;
}) {
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    // Base UI skips enter transitions for roots that mount already open.
    setMounted(true);
  }, []);

  return (
    <Drawer
      open={open && mounted}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      onOpenChangeComplete={(open) => {
        if (!open) onClosed();
      }}
      disablePointerDismissal
    >
      <DrawerContent width={width} data-base-ui-swipe-ignore="">
        <header className="flex shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-5 py-3">
          <DrawerTitle className="text-base font-semibold">{title}</DrawerTitle>
          <Button variant="ghost" size="sm" aria-label={`关闭${title}`} onClick={onClose}>
            <X size={16} />
          </Button>
        </header>
        <DrawerBody>{children}</DrawerBody>
        <footer className="flex shrink-0 items-center gap-2 border-t border-[var(--yak-components-panel-border)] px-5 py-3">
          {footer || (
            <Button variant="secondary" size="sm" onClick={onClose}>
              关闭
            </Button>
          )}
        </footer>
      </DrawerContent>
    </Drawer>
  );
}
