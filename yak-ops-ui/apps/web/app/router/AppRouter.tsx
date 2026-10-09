import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { DataSourcePage } from "@/app/datasource";
import LoginPage from "@/app/login";
import { UserManagementPage, WorkspaceManagementPage } from "@/app/management";
import {
  OfflineSyncEditorPage,
  OfflineSyncInstanceDetailPage,
  OfflineSyncPage,
  OfflineSyncTaskDetailPage,
} from "@/app/offline-sync";
import {
  OfflineTaskOperationsInstanceDetailPage,
  OfflineTaskOperationsPage,
  RealtimeTaskOperationsInstanceDetailPage,
  RealtimeTaskOperationsPage,
} from "@/app/operations";
import {
  RealtimeSyncEditorPage,
  RealtimeSyncInstanceDetailPage,
  RealtimeSyncPage,
  RealtimeSyncTaskDetailPage,
} from "@/app/realtime-sync";
import { useAuth } from "@/hooks/use-auth";

import AppLayout from "../layout/AppLayout";
import {
  DATA_INTEGRATION_NAVIGATION,
  DATA_INTEGRATION_PRODUCT_LABEL,
  MANAGEMENT_NAVIGATION,
  MANAGEMENT_PRODUCT_LABEL,
  OPERATIONS_NAVIGATION,
  OPERATIONS_PRODUCT_LABEL,
} from "../layout/navigation";

const DEFAULT_AUTHENTICATED_PATH = "/data-source";
const AUTHENTICATED_PATHS = new Set([
  "/data-source",
  "/offline-sync",
  "/offline-sync/new",
  "/realtime-sync",
  "/realtime-sync/new",
  "/operations",
  "/operations/offline-tasks",
  "/operations/realtime-tasks",
  "/management/users",
  "/management/workspaces",
]);

const isAuthenticatedPath = (pathname: string) =>
  AUTHENTICATED_PATHS.has(pathname) ||
  pathname.startsWith("/offline-sync/") ||
  pathname.startsWith("/realtime-sync/") ||
  pathname.startsWith("/operations/");

const resolveReturnTo = (requested: string | null) => {
  if (!requested) return DEFAULT_AUTHENTICATED_PATH;

  try {
    const destination = new URL(requested, window.location.origin);
    if (
      destination.origin !== window.location.origin ||
      !isAuthenticatedPath(destination.pathname)
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
    return <Navigate replace to={`/login?returnTo=${encodeURIComponent(returnTo)}`} />;
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
        <Route
          element={
            <AppLayout
              productLabel={DATA_INTEGRATION_PRODUCT_LABEL}
              productPath="/data-source"
              navigation={DATA_INTEGRATION_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route path="/data-source" element={<DataSourcePage />} />
          <Route path="/offline-sync" element={<OfflineSyncPage />} />
          <Route path="/offline-sync/new" element={<OfflineSyncEditorPage />} />
          <Route path="/offline-sync/instances/:id" element={<OfflineSyncInstanceDetailPage />} />
          <Route path="/offline-sync/:taskId/detail" element={<OfflineSyncTaskDetailPage />} />
          <Route path="/offline-sync/:id" element={<OfflineSyncEditorPage />} />
          <Route path="/realtime-sync" element={<RealtimeSyncPage />} />
          <Route path="/realtime-sync/new" element={<RealtimeSyncEditorPage />} />
          <Route path="/realtime-sync/instances/:id" element={<RealtimeSyncInstanceDetailPage />} />
          <Route path="/realtime-sync/:taskId/detail" element={<RealtimeSyncTaskDetailPage />} />
          <Route path="/realtime-sync/:id" element={<RealtimeSyncEditorPage />} />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={OPERATIONS_PRODUCT_LABEL}
              productPath="/operations/offline-tasks"
              navigation={OPERATIONS_NAVIGATION}
              workspaceScoped
            />
          }
        >
          <Route path="/operations" element={<Navigate replace to="/operations/offline-tasks" />} />
          <Route path="/operations/offline-tasks" element={<OfflineTaskOperationsPage />} />
          <Route
            path="/operations/offline-tasks/instances/:id"
            element={<OfflineTaskOperationsInstanceDetailPage />}
          />
          <Route path="/operations/realtime-tasks" element={<RealtimeTaskOperationsPage />} />
          <Route
            path="/operations/realtime-tasks/instances/:id"
            element={<RealtimeTaskOperationsInstanceDetailPage />}
          />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={MANAGEMENT_PRODUCT_LABEL}
              productPath="/management/users"
              navigation={MANAGEMENT_NAVIGATION}
            />
          }
        >
          <Route path="/management" element={<Navigate replace to="/management/users" />} />
          <Route path="/management/users" element={<UserManagementPage />} />
          <Route path="/management/workspaces" element={<WorkspaceManagementPage />} />
        </Route>

        <Route path="/" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
        <Route path="*" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
      </Route>
    </Routes>
  );
}
