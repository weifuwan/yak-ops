import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import AppLayout from "@/app/layout/AppLayout";
import { useAuth } from "@/app/providers/AuthProvider";
import DataSourcePage from "@/pages/data-source";
import LoginPage from "@/pages/login";

const DEFAULT_AUTHENTICATED_PATH = "/data-source";

const resolveReturnTo = (requested: string | null) => {
  if (!requested) return DEFAULT_AUTHENTICATED_PATH;

  try {
    const destination = new URL(requested, window.location.origin);
    if (
      destination.origin !== window.location.origin ||
      destination.pathname !== DEFAULT_AUTHENTICATED_PATH
    ) {
      return DEFAULT_AUTHENTICATED_PATH;
    }
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return DEFAULT_AUTHENTICATED_PATH;
  }
};

function ProtectedRoute() {
  const { currentUser, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#f7f8f9] text-sm text-black/45">
        Loading...
      </div>
    );
  }

  if (!currentUser) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`;
    return (
      <Navigate
        replace
        to={`/login?returnTo=${encodeURIComponent(returnTo)}`}
      />
    );
  }

  return <Outlet />;
}

function LoginRoute() {
  const { currentUser, loading, refreshCurrentUser } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#fbfbfa] text-sm text-black/45">
        Loading...
      </div>
    );
  }

  if (currentUser) {
    return <Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />;
  }

  const handleAuthenticated = async () => {
    await refreshCurrentUser();
    const requested = new URLSearchParams(location.search).get("returnTo");
    navigate(resolveReturnTo(requested), { replace: true });
  };

  return <LoginPage onAuthenticated={handleAuthenticated} />;
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/data-source" element={<DataSourcePage />} />
          <Route path="/" element={<Navigate replace to="/data-source" />} />
          <Route path="*" element={<Navigate replace to="/data-source" />} />
        </Route>
      </Route>
    </Routes>
  );
}
