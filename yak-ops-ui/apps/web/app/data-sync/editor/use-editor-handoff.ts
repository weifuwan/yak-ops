import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { ScheduleTimeRule } from "@/service/schedule";
import type { EditorWriteRecovery } from "./use-editor-submission";

interface EditorHandoff {
  scheduleDraft?: ScheduleTimeRule;
  scheduleSaveError?: string;
  writeRecovery?: EditorWriteRecovery;
}

/** Consume partial-write recovery once; browser history must never replay an obsolete Cron draft. */
export function useEditorHandoff(id?: string) {
  const location = useLocation();
  const navigate = useNavigate();
  const [handoff] = useState(() => location.state as EditorHandoff | null);
  useEffect(() => {
    if (!id || !location.state) return;
    const keys = ["scheduleDraft", "scheduleSaveError", "writeRecovery"];
    if (!keys.some((key) => Object.hasOwn(location.state, key))) return;
    const remaining = Object.fromEntries(
      Object.entries(location.state).filter(([key]) => !keys.includes(key)),
    );
    navigate(`${location.pathname}${location.search}${location.hash}`, {
      replace: true,
      state: Object.keys(remaining).length ? remaining : null,
    });
  }, [id, location.state, location.pathname, location.search, location.hash, navigate]);
  return handoff;
}
