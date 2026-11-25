import { SplitSparkIcon } from "@yak-ops/yak-ui";
import { useState } from "react";

import LoginPanel from "./LoginPanel";
import LoginWelcomeScene from "./LoginWelcomeScene";
import WeChatQrHelp from "./WeChatQrHelp";
import type { LoginFocusState, LoginResultState } from "./login-interaction";

import "./login-form.css";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [focusState, setFocusState] = useState<LoginFocusState>("idle");
  const [resultState, setResultState] = useState<LoginResultState>("idle");
  const [passwordVisible, setPasswordVisible] = useState(false);

  return (
    <main className="h-full min-h-screen overflow-y-auto bg-white md:grid md:grid-cols-[7fr_5fr]">
      <section className="hidden min-h-screen overflow-hidden md:block">
        <LoginWelcomeScene
          focusState={focusState}
          resultState={resultState}
          passwordVisible={passwordVisible}
        />
      </section>

      <section
        aria-labelledby="login-title"
        className="flex min-h-screen min-w-0 flex-col items-center bg-white px-6 py-8 sm:px-10 lg:px-14"
      >
        <div className="flex w-full max-w-[400px] flex-1 flex-col">
          <div className="flex min-h-0 flex-1 items-center py-12">
            <div className="w-full">
              <div className="mb-6 text-center">
                <SplitSparkIcon
                  size={42}
                  className="mx-auto mb-7 text-[var(--yak-components-page-header-title)]"
                />
                <h1
                  id="login-title"
                  className="m-0 text-balance text-[30px] font-bold leading-[1.15] tracking-[-0.025em] text-[var(--yak-components-page-header-title)]"
                >
                  Welcome back!
                </h1>
                <p className="mt-2.5 text-sm leading-6 text-[var(--yak-components-button-ghost-text)]">
                  Please enter your details.
                </p>
              </div>

              <LoginPanel
                onAuthenticated={onAuthenticated}
                onFocusStateChange={setFocusState}
                onLoginResultChange={setResultState}
                onPasswordVisibilityChange={setPasswordVisible}
              />
            </div>
          </div>
          <div className="shrink-0 pt-6">
            <WeChatQrHelp />
          </div>
        </div>
      </section>
    </main>
  );
}
