import { Button, Modal } from "@yak-ops/yak-ui";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useBeforeUnload, useBlocker } from "react-router-dom";

interface LeaveGuardContextValue {
  register: (warning: () => string | undefined) => () => void;
  confirmLeave: () => Promise<boolean>;
}

const LeaveGuardContext = createContext<LeaveGuardContextValue | null>(null);

/** Coordinates route changes and explicit identity changes without owning any domain form. */
export function LeaveGuardProvider({ children }: { children: ReactNode }) {
  const warnings = useRef(new Set<() => string | undefined>());
  const pending = useRef<((proceed: boolean) => void) | undefined>();
  const [pendingMessage, setPendingMessage] = useState<string>();
  const warning = useCallback(() => {
    for (const read of warnings.current) {
      const message = read();
      if (message) return message;
    }
    return undefined;
  }, []);
  const register = useCallback((read: () => string | undefined) => {
    warnings.current.add(read);
    return () => {
      warnings.current.delete(read);
    };
  }, []);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      // Authentication recovery must remain possible even when a form cannot be saved.
      nextLocation.pathname !== "/login" &&
      currentLocation.pathname !== nextLocation.pathname &&
      Boolean(warning()),
  );
  useBeforeUnload(
    useCallback(
      (event: BeforeUnloadEvent) => {
        if (!warning()) return;
        event.preventDefault();
        event.returnValue = "";
      },
      [warning],
    ),
  );
  const confirmLeave = useCallback((): Promise<boolean> => {
    const message = warning();
    if (!message) return Promise.resolve(true);
    if (pending.current) return Promise.resolve(false);
    setPendingMessage(message);
    return new Promise((resolve) => {
      pending.current = resolve;
    });
  }, [warning]);
  useEffect(
    () => () => {
      pending.current?.(false);
    },
    [],
  );

  const resolve = (proceed: boolean) => {
    const complete = pending.current;
    pending.current = undefined;
    setPendingMessage(undefined);
    complete?.(proceed);
    if (blocker.state === "blocked") {
      if (proceed) blocker.proceed();
      else blocker.reset();
    }
  };
  const message = pendingMessage || (blocker.state === "blocked" ? warning() : undefined);
  useEffect(() => {
    if (blocker.state === "blocked" && !message) blocker.proceed();
  }, [blocker, message]);
  return (
    <LeaveGuardContext.Provider value={{ register, confirmLeave }}>
      {children}
      <Modal
        open={Boolean(message)}
        centered
        width={420}
        title="离开当前页面"
        onClose={() => resolve(false)}
        footer={
          <>
            <Button size="small" onClick={() => resolve(false)}>
              继续编辑
            </Button>
            <Button size="small" variant="primary" onClick={() => resolve(true)}>
              放弃修改并离开
            </Button>
          </>
        }
      >
        <p className="text-sm">{message}</p>
      </Modal>
    </LeaveGuardContext.Provider>
  );
}

export function useLeaveGuard() {
  const context = useContext(LeaveGuardContext);
  if (!context) throw new Error("useLeaveGuard must be used inside LeaveGuardProvider");
  return context;
}
