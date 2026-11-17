import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from "react";

export type PageAgentContext = {
  type: string;
  id?: string;
  label: string;
} | null;

type AgentContextValue = {
  context: PageAgentContext;
  setContext: (context: PageAgentContext) => void;
};

const AgentContext = createContext<AgentContextValue>({
  context: null,
  setContext: () => undefined,
});

export function AgentContextProvider({ children }: PropsWithChildren) {
  const [context, setContext] = useState<PageAgentContext>(null);
  const setAgentContext = useCallback((next: PageAgentContext) => setContext(next), []);
  const value = useMemo(
    () => ({ context, setContext: setAgentContext }),
    [context, setAgentContext],
  );
  return <AgentContext.Provider value={value}>{children}</AgentContext.Provider>;
}

export function useAgentContext(): PageAgentContext {
  return useContext(AgentContext).context;
}

/** Pages declare the object they currently display; the drawer shows it as a context tag. */
export function usePageAgentContext(context: PageAgentContext) {
  const { setContext } = useContext(AgentContext);
  const type = context?.type;
  const id = context?.id;
  const label = context?.label;
  useEffect(() => {
    setContext(type && label ? { type, id, label } : null);
    return () => setContext(null);
  }, [type, id, label, setContext]);
}
