import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

import {
  getCurrentUser,
  type CurrentUser,
} from "@/services/security/account";
import { toCurrentUser } from "@/services/security/currentIdentity";
import { AUTHENTICATION_INVALIDATED_EVENT } from "@/utils/security/authentication";

interface AuthContextValue {
  currentUser?: CurrentUser;
  loading: boolean;
  refreshCurrentUser: () => Promise<CurrentUser | undefined>;
  clearCurrentUser: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<CurrentUser>();
  const [loading, setLoading] = useState(true);

  const refreshCurrentUser = async () => {
    const user = toCurrentUser(await getCurrentUser());
    setCurrentUser(user);
    return user;
  };

  const clearCurrentUser = () => {
    setCurrentUser(undefined);
  };

  useEffect(() => {
    let active = true;

    getCurrentUser({ skipErrorHandler: true })
      .then((user) => {
        if (active) setCurrentUser(toCurrentUser(user));
      })
      .catch(() => {
        if (active) setCurrentUser(undefined);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    const clearAuthentication = () => {
      setCurrentUser(undefined);
    };

    window.addEventListener(
      AUTHENTICATION_INVALIDATED_EVENT,
      clearAuthentication,
    );

    return () => {
      active = false;
      window.removeEventListener(
        AUTHENTICATION_INVALIDATED_EVENT,
        clearAuthentication,
      );
    };
  }, []);

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        loading,
        refreshCurrentUser,
        clearCurrentUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
