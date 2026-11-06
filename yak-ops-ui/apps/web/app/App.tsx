import { ToastProvider } from "@yak-ops/yak-ui";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router-dom";

import { AuthProvider } from "@/context/auth-context";
import { WorkspaceProvider } from "@/context/workspace-context";
import { LeaveGuardProvider } from "@/context/leave-guard-context";

import AppRouter from "./router/AppRouter";

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
const router = createBrowserRouter([
  {
    path: "*",
    element: (
      <AuthProvider>
        <LeaveGuardProvider>
          <WorkspaceProvider>
            <AppRouter />
          </WorkspaceProvider>
        </LeaveGuardProvider>
      </AuthProvider>
    ),
  },
]);

export default function App() {
  return (
    <ToastProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ToastProvider>
  );
}
