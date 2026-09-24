import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";

import AppLayout from "@/app/layout/AppLayout";
import { useAuth } from "@/app/providers/AuthProvider";
import DataSourcePage from "@/pages/data-source";
import LoginPage from "@/pages/login";

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
  const { currentUser, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#fbfbfa] text-sm text-black/45">
        Loading...
      </div>
    );
  }

  return currentUser ? <Navigate replace to="/data-source" /> : <LoginPage />;
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
