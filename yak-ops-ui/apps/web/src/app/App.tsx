import { BrowserRouter } from "react-router-dom";

import { AuthProvider } from "./providers/AuthProvider";
import AppRouter from "./router/AppRouter";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </BrowserRouter>
  );
}
