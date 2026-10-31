import { useEffect, useRef } from "react";
import { useLeaveGuard } from "@/context/leave-guard-context";

/** Guards external navigation while keeping same-page tabs and filters usable. */
export function useUnsavedChanges(when: boolean, message = "当前修改尚未保存，离开后将丢失。") {
  const current = useRef({ when, message });
  current.current = { when, message };
  const { register } = useLeaveGuard();
  useEffect(
    () => register(() => (current.current.when ? current.current.message : undefined)),
    [register],
  );
}
