import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { DataSourcePage } from "@/app/datasource";
import LoginPage from "@/app/login";
import { UserManagementPage, WorkspaceManagementPage } from "@/app/management";
import { DataSyncPage } from "@/app/data-sync";
import { DataSyncTaskEditorPage } from "@/app/data-sync/task-editor";
import { DataSyncTaskDetailPage } from "@/app/data-sync/task-detail";
import { DataSyncRouteRedirect, DataSyncInstanceRedirect } from "@/app/data-sync/redirects";
import {
  OfflineTaskOperationsInstanceDetailPage,
  OfflineTaskOperationsPage,
  RealtimeTaskOperationsInstanceDetailPage,
  RealtimeTaskOperationsPage,
} from "@/app/operations";
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
import AppLoading from "./AppLoading";

const DEFAULT_AUTHENTICATED_PATH = "/data-source";
const AUTHENTICATED_PATHS = new Set([
  "/data-source",
  "/data-sync",
  "/data-sync/new",
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
  pathname.startsWith("/data-sync/") ||
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
    return <AppLoading />;
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
    return <AppLoading />;
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
          <Route path="/data-sync" element={<DataSyncPage />} />
          <Route path="/data-sync/new" element={<DataSyncTaskEditorPage />} />
          <Route path="/data-sync/:id/edit" element={<DataSyncTaskEditorPage />} />
          <Route path="/data-sync/:id/detail" element={<DataSyncTaskDetailPage />} />
          <Route path="/offline-sync" element={<DataSyncRouteRedirect kind="list" />} />
          <Route path="/offline-sync/new" element={<DataSyncRouteRedirect kind="new" />} />
          <Route path="/offline-sync/instances/:id" element={<DataSyncInstanceRedirect />} />
          <Route
            path="/offline-sync/:taskId/detail"
            element={<DataSyncRouteRedirect kind="detail" />}
          />
          <Route path="/offline-sync/:id" element={<DataSyncRouteRedirect kind="edit" />} />
          <Route path="/realtime-sync" element={<DataSyncRouteRedirect kind="list" />} />
          <Route path="/realtime-sync/new" element={<DataSyncRouteRedirect kind="new" />} />
          <Route path="/realtime-sync/instances/:id" element={<DataSyncInstanceRedirect />} />
          <Route
            path="/realtime-sync/:taskId/detail"
            element={<DataSyncRouteRedirect kind="detail" />}
          />
          <Route path="/realtime-sync/:id" element={<DataSyncRouteRedirect kind="edit" />} />
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
