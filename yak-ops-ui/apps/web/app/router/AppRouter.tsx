import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { DataDevelopmentPage } from "@/app/data-development/page";
import { DataModelingOverviewPage } from "@/app/data-modeling/overview/page";
import { DataModelingStandardsPage } from "@/app/data-modeling/standards/page";
import { DataModelingIndicatorsPage } from "@/app/data-modeling/indicators/page";
import { DataModelingPlanningPage } from "@/app/data-modeling/planning/page";
import { DataModelingSectionPage } from "@/app/data-modeling/section/page";
import { ResourceManagementPage } from "@/app/resources/page";
import { TaskGroupConfigurationPage } from "@/app/resources/task-group-option/page";
import { TaskGroupQueuePage } from "@/app/resources/task-group-queue/page";
import { DataSourcePage } from "@/app/datasource/list/page";
import { DataIntegrationOverviewPage } from "@/app/data-integration/overview/page";
import LoginPage from "@/app/login";
import { AlertCenterPage } from "@/app/management/alerts/page";
import { UserManagementPage } from "@/app/management/users/page";
import { WorkspaceManagementPage } from "@/app/management/workspaces/page";
import { DataSyncPage } from "@/app/data-sync/list/page";
import {
  DataSyncTaskEditorPage,
  DatabaseSyncTaskEditorPage,
  MultiTableSyncTaskEditorPage,
  NewDataSyncTaskEditorRedirect,
  SingleTableSyncTaskEditorPage,
} from "@/app/data-sync/editor/page";
import { DataSyncTaskDetailPage } from "@/app/data-sync/detail";
import { DataSyncInstanceRedirect } from "./data-sync-instance-redirect";
import {
  OfflineTaskOperationsPage,
  RealtimeTaskOperationsPage,
} from "@/app/operations/dashboard/page";
import { useAuth } from "@/hooks/use-auth";

import AppLayout from "../layout/AppLayout";
import {
  DATA_INTEGRATION_NAVIGATION,
  DATA_INTEGRATION_PRODUCT_LABEL,
  DATA_MODELING_MODULE_NAVIGATION,
  DATA_MODELING_PRODUCT_LABEL,
  MANAGEMENT_NAVIGATION,
  MANAGEMENT_PRODUCT_LABEL,
  OPERATIONS_NAVIGATION,
  OPERATIONS_PRODUCT_LABEL,
  RESOURCE_MANAGEMENT_NAVIGATION,
  RESOURCE_MANAGEMENT_PRODUCT_LABEL,
} from "../layout/navigation";
import AppLoading from "./AppLoading";
import WorkspaceGate from "./WorkspaceGate";

const DEFAULT_AUTHENTICATED_PATH = "/overview";
const AUTHENTICATED_PATHS = new Set([
  "/overview",
  "/data-development",
  "/data-modeling",
  "/resources",
  "/resources/task-groups",
  "/resources/task-group-queue",
  "/data-source",
  "/data-sync",
  "/data-sync/new",
  "/operations",
  "/operations/offline-tasks",
  "/operations/realtime-tasks",
  "/management/users",
  "/management/workspaces",
  "/management/alerts",
]);

const isAuthenticatedPath = (pathname: string) =>
  AUTHENTICATED_PATHS.has(pathname) ||
  pathname.startsWith("/data-modeling/") ||
  pathname.startsWith("/data-sync/") ||
  pathname.startsWith("/operations/") ||
  pathname.startsWith("/resources/");

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
        <Route element={<WorkspaceGate />}>
          <Route
            element={
              <AppLayout
                productLabel={DATA_INTEGRATION_PRODUCT_LABEL}
                productPath="/overview"
                navigation={DATA_INTEGRATION_NAVIGATION}
              />
            }
          >
            <Route path="/overview" element={<DataIntegrationOverviewPage />} />
            <Route path="/data-source" element={<DataSourcePage />} />
            <Route path="/data-sync" element={<DataSyncPage />} />
            <Route path="/data-sync/new" element={<NewDataSyncTaskEditorRedirect />} />
            <Route path="/data-sync/new/database" element={<DatabaseSyncTaskEditorPage />} />
            <Route
              path="/data-sync/new/multi-table"
              element={<MultiTableSyncTaskEditorPage />}
            />
            <Route
              path="/data-sync/new/single-table"
              element={<SingleTableSyncTaskEditorPage />}
            />
            <Route path="/data-sync/:id/edit" element={<DataSyncTaskEditorPage />} />
            <Route path="/data-sync/:id/detail" element={<DataSyncTaskDetailPage />} />
          </Route>

          <Route path="/data-development" element={<DataDevelopmentPage />} />

          <Route
            element={
              <AppLayout
                productLabel={DATA_MODELING_PRODUCT_LABEL}
                productPath="/data-modeling"
                moduleNavigation={DATA_MODELING_MODULE_NAVIGATION}
              />
            }
          >
            <Route path="/data-modeling" element={<DataModelingOverviewPage />} />

            <Route
              path="/data-modeling/planning"
              element={<Navigate replace to="/data-modeling/planning/business-type" />}
            />
            <Route
              path="/data-modeling/planning/business-type"
              element={<DataModelingPlanningPage definitionKey="businessType" />}
            />
            <Route
              path="/data-modeling/planning/dw-layer"
              element={<DataModelingPlanningPage definitionKey="dwLayer" />}
            />
            <Route
              path="/data-modeling/planning/common-layer/data-domain"
              element={<DataModelingPlanningPage definitionKey="dataDomain" />}
            />
            <Route
              path="/data-modeling/planning/common-layer/business-process"
              element={<DataModelingPlanningPage definitionKey="businessProcess" />}
            />
            <Route
              path="/data-modeling/planning/application-layer/data-market"
              element={<DataModelingPlanningPage definitionKey="dataMart" />}
            />
            <Route
              path="/data-modeling/planning/application-layer/subject-domain"
              element={<DataModelingPlanningPage definitionKey="subjectDomain" />}
            />

            <Route
              path="/data-modeling/models"
              element={<Navigate replace to="/data-modeling/models/positive" />}
            />
            <Route
              path="/data-modeling/models/positive"
              element={
                <DataModelingSectionPage
                  title="维度建模"
                  description="围绕维度、维度表和数仓模型，逐步迁移原 data_modal 的维度建模能力。"
                  capabilities={["维度管理", "维度表设计", "模型字段", "版本管理"]}
                />
              }
            />
            <Route
              path="/data-modeling/models/reverse"
              element={
                <DataModelingSectionPage
                  title="逆向建模"
                  description="预留数据库逆向解析、表结构导入和模型生成入口，后续逐步迁移原有功能。"
                  capabilities={["数据源选择", "表结构读取", "模型反向生成", "模型校验"]}
                />
              }
            />

            <Route
              path="/data-modeling/standards"
              element={<Navigate replace to="/data-modeling/standards/fields" />}
            />
            <Route
              path="/data-modeling/standards/fields"
              element={<DataModelingStandardsPage initialTab={1} />}
            />
            <Route
              path="/data-modeling/standards/codes"
              element={<DataModelingStandardsPage initialTab={3} />}
            />
            <Route
              path="/data-modeling/standards/units"
              element={<DataModelingStandardsPage initialTab={6} />}
            />
            <Route
              path="/data-modeling/standards/dictionary"
              element={<DataModelingStandardsPage initialTab={7} />}
            />

            <Route
              path="/data-modeling/indicators"
              element={<Navigate replace to="/data-modeling/indicators/compose" />}
            />
            <Route
              path="/data-modeling/indicators/:indicatorKind"
              element={<DataModelingIndicatorsPage />}
            />
          </Route>

          <Route
            element={
              <AppLayout
                productLabel={OPERATIONS_PRODUCT_LABEL}
                productPath="/operations/offline-tasks"
                navigation={OPERATIONS_NAVIGATION}
              />
            }
          >
            <Route
              path="/operations"
              element={<Navigate replace to="/operations/offline-tasks" />}
            />
            <Route path="/operations/offline-tasks" element={<OfflineTaskOperationsPage />} />
            <Route
              path="/operations/offline-tasks/instances/:id"
              element={<DataSyncInstanceRedirect />}
            />
            <Route path="/operations/realtime-tasks" element={<RealtimeTaskOperationsPage />} />
            <Route
              path="/operations/realtime-tasks/instances/:id"
              element={<DataSyncInstanceRedirect />}
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
            <Route path="/management/alerts" element={<AlertCenterPage />} />
          </Route>

          <Route path="/" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
          <Route path="*" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
        </Route>

        <Route
          element={
            <AppLayout
              productLabel={RESOURCE_MANAGEMENT_PRODUCT_LABEL}
              productPath="/resources"
              navigation={RESOURCE_MANAGEMENT_NAVIGATION}
            />
          }
        >
          <Route path="/resources" element={<ResourceManagementPage />} />
          <Route path="/resources/task-groups" element={<TaskGroupConfigurationPage />} />
          <Route path="/resources/task-group-queue" element={<TaskGroupQueuePage />} />
        </Route>
      </Route>
    </Routes>
  );
}
