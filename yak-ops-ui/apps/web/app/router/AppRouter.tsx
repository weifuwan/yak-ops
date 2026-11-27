import { useEffect } from "react";
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { DataAgentPage } from "@/app/data-agent/page";
import { ParticlePreviewPage } from "@/app/data-agent/particle-preview/page";
import { DataDevelopmentPage } from "@/app/data-development/page";
import { DataModelingOverviewPage } from "@/app/data-modeling/overview";
import FieldStandardPage from "@/app/data-modeling/standards/field-standard";
import CodeStandardPage from "@/app/data-modeling/standards/code-standard";
import UnitStandardPage from "@/app/data-modeling/standards/unit-standard";
import NamingDictionaryPage from "@/app/data-modeling/standards/naming-dictionary";
import IndicatorsIndexPage from "@/app/data-modeling/indicators";
import BusinessTypePage from "@/app/data-modeling/planning/business-type";
import DwLayerPage from "@/app/data-modeling/planning/dw-layer";
import DataDomainPage from "@/app/data-modeling/planning/common-layer/data-domain";
import BusinessProcessPage from "@/app/data-modeling/planning/common-layer/business-process";
import DataMartPage from "@/app/data-modeling/planning/application-layer/data-mart";
import SubjectDomainPage from "@/app/data-modeling/planning/application-layer/subject-domain";
import DimensionModelingPage from "@/app/data-modeling/models/dimension-modeling";
import ReverseModelingPage from "@/app/data-modeling/models/reverse-modeling";
import { ResourceManagementPage } from "@/app/resources/page";
import { WorkerGroupsPage } from "@/app/resources/worker-groups/page";
import { EnvironmentsPage } from "@/app/resources/environments/page";
import { TaskGroupConfigurationPage } from "@/app/resources/task-group-option/page";
import { TaskGroupQueuePage } from "@/app/resources/task-group-queue/page";
import { DataSourcePage } from "@/app/datasource/list/page";
import { DataIntegrationOverviewPage } from "@/app/data-integration/overview/page";
import LoginPage from "@/app/login";
import { AlertCenterPage } from "@/app/management/alerts/page";
import { TenantManagementPage } from "@/app/management/tenants/page";
import { UserManagementPage } from "@/app/management/users/page";
import { AccessTokenManagementPage } from "@/app/management/tokens/page";
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
import { OperationsOverviewPage } from "@/app/operations/overview/page";
import { SystemMonitorPage } from "@/app/operations/system-monitor/page";
import { OperationsAlertsPage } from "@/app/operations/alerts/page";
import {
  WorkflowInstancesOperationsPage,
  WorkflowSchedulesOperationsPage,
} from "@/app/operations/workflow/page";
import {
  OfflineTasksOperationsPage,
  RealtimeTasksOperationsPage,
} from "@/app/operations/data-sync/tasks-page";
import {
  OfflineInstancesOperationsPage,
  RealtimeInstancesOperationsPage,
} from "@/app/operations/data-sync/instances-page";
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
import { resolveDocumentTitle } from "../layout/document-title";
import AppLoading from "./AppLoading";
import WorkspaceGate from "./WorkspaceGate";

const DEFAULT_AUTHENTICATED_PATH = "/overview";
const AUTHENTICATED_PATHS = new Set([
  "/overview",
  "/data-development",
  "/data-agent",
  "/data-agent/particle-preview",
  "/kimball",
  "/resources",
  "/resources/worker-groups",
  "/resources/environments",
  "/resources/task-groups",
  "/resources/task-group-queue",
  "/data-source",
  "/data-sync",
  "/data-sync/new",
  "/operations",
  "/operations/offline-tasks",
  "/operations/realtime-tasks",
  "/management/tenants",
  "/management/users",
  "/management/tokens",
  "/management/workspaces",
  "/management/alerts",
]);

const isAuthenticatedPath = (pathname: string) =>
  AUTHENTICATED_PATHS.has(pathname) ||
  pathname.startsWith("/data-agent/") ||
  pathname.startsWith("/kimball/") ||
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
  const location = useLocation();

  useEffect(() => {
    document.title = resolveDocumentTitle(location.pathname, location.search);
  }, [location.pathname, location.search]);

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
          <Route path="/data-agent" element={<DataAgentPage />} />
          <Route path="/data-agent/new" element={<DataAgentPage />} />
          <Route path="/data-agent/sessions/:sessionId" element={<DataAgentPage />} />

          <Route
            element={
              <AppLayout
                productLabel={DATA_MODELING_PRODUCT_LABEL}
                productPath="/kimball"
                moduleNavigation={DATA_MODELING_MODULE_NAVIGATION}
              />
            }
          >
            <Route path="/kimball" element={<DataModelingOverviewPage />} />

            <Route
              path="/kimball/planning"
              element={<Navigate replace to="/kimball/planning/business-type" />}
            />
            <Route
              path="/kimball/planning/business-type"
              element={<BusinessTypePage />}
            />
            <Route
              path="/kimball/planning/dw-layer"
              element={<DwLayerPage />}
            />
            <Route
              path="/kimball/planning/common-layer/data-domain"
              element={<DataDomainPage />}
            />
            <Route
              path="/kimball/planning/common-layer/business-process"
              element={<BusinessProcessPage />}
            />
            <Route
              path="/kimball/planning/application-layer/data-market"
              element={<DataMartPage />}
            />
            <Route
              path="/kimball/planning/application-layer/subject-domain"
              element={<SubjectDomainPage />}
            />

            <Route
              path="/kimball/models"
              element={<Navigate replace to="/kimball/models/positive" />}
            />
            <Route
              path="/kimball/models/positive"
              element={
                <DimensionModelingPage />
              }
            />
            <Route
              path="/kimball/models/reverse"
              element={
                <ReverseModelingPage />
              }
            />

            <Route
              path="/kimball/standards"
              element={<Navigate replace to="/kimball/standards/fields" />}
            />
            <Route
              path="/kimball/standards/fields"
              element={<FieldStandardPage />}
            />
            <Route
              path="/kimball/standards/codes"
              element={<CodeStandardPage />}
            />
            <Route
              path="/kimball/standards/units"
              element={<UnitStandardPage />}
            />
            <Route
              path="/kimball/standards/dictionary"
              element={<NamingDictionaryPage />}
            />

            <Route path="/kimball/indicators" element={<IndicatorsIndexPage />} />
            <Route path="/kimball/indicators/*" element={<IndicatorsIndexPage />} />
          </Route>

          <Route
            element={
              <AppLayout
                productLabel={OPERATIONS_PRODUCT_LABEL}
                productPath="/operations/overview"
                navigation={OPERATIONS_NAVIGATION}
              />
            }
          >
            <Route
              path="/operations"
              element={<Navigate replace to="/operations/overview" />}
            />
            <Route path="/operations/overview" element={<OperationsOverviewPage />} />
            <Route path="/operations/system-monitor" element={<SystemMonitorPage />} />
            <Route path="/operations/alerts" element={<OperationsAlertsPage />} />
            <Route path="/operations/workflows/instances" element={<WorkflowInstancesOperationsPage />} />
            <Route path="/operations/workflows/schedules" element={<WorkflowSchedulesOperationsPage />} />
            <Route path="/operations/offline/tasks" element={<OfflineTasksOperationsPage />} />
            <Route path="/operations/offline/instances" element={<OfflineInstancesOperationsPage />} />
            <Route path="/operations/realtime/tasks" element={<RealtimeTasksOperationsPage />} />
            <Route path="/operations/realtime/instances" element={<RealtimeInstancesOperationsPage />} />
            <Route path="/operations/offline-tasks" element={<Navigate replace to="/operations/offline/tasks" />} />
            <Route path="/operations/realtime-tasks" element={<Navigate replace to="/operations/realtime/tasks" />} />
            <Route
              path="/operations/offline-tasks/instances/:id"
              element={<DataSyncInstanceRedirect />}
            />
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
            <Route path="/management/tenants" element={<TenantManagementPage />} />
            <Route path="/management/users" element={<UserManagementPage />} />
            <Route path="/management/tokens" element={<AccessTokenManagementPage />} />
            <Route path="/management/workspaces" element={<WorkspaceManagementPage />} />
            <Route path="/management/alerts" element={<AlertCenterPage />} />
          </Route>

          <Route path="/" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
          <Route path="*" element={<Navigate replace to={DEFAULT_AUTHENTICATED_PATH} />} />
        </Route>

        <Route
          path="/data-agent/particle-preview"
          element={<ParticlePreviewPage />}
        />

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
          <Route path="/resources/worker-groups" element={<WorkerGroupsPage />} />
          <Route path="/resources/environments" element={<EnvironmentsPage />} />
          <Route path="/resources/task-groups" element={<TaskGroupConfigurationPage />} />
          <Route path="/resources/task-group-queue" element={<TaskGroupQueuePage />} />
        </Route>
      </Route>
    </Routes>
  );
}
