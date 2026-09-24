import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

import {
  getCurrentUser,
  type AuthUser,
} from "../../service/auth";

interface AuthContextValue {
  currentUser?: AuthUser;
  loading: boolean;
  refreshCurrentUser: () => Promise<AuthUser>;
  clearCurrentUser: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<AuthUser>();
  const [loading, setLoading] = useState(true);

  const refreshCurrentUser = async () => {
    const user = await getCurrentUser();
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
        if (active) setCurrentUser(user);
      })
      .catch(() => {
        if (active) setCurrentUser(undefined);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
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
