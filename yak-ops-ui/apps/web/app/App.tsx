import { ToastProvider } from "@yak-ops/yak-ui";
import { BrowserRouter } from "react-router-dom";

import { AuthProvider } from "@/context/auth-context";
import { WorkspaceProvider } from "@/context/workspace-context";

import AppRouter from "./router/AppRouter";

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <AuthProvider>
          <WorkspaceProvider>
            <AppRouter />
          </WorkspaceProvider>
        </AuthProvider>
      </BrowserRouter>
    </ToastProvider>
  );
}
