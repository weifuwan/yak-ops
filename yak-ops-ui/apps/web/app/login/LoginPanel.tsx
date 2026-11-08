import {
  Button,
  Field,
  FieldError,
  FloatingLabelField,
  Input,
  PasswordInput,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { notifyOnce } from "@/utils/notification";
import { login } from "../../service/auth";
import LoginPasswordEye from "./LoginPasswordEye";
import {
  LOGIN_FAILURE_MOTION_MS,
  LOGIN_SUCCESS_MOTION_MS,
  type LoginFocusState,
  type LoginResultState,
} from "./login-interaction";

interface LoginPanelProps {
  onAuthenticated: () => Promise<void>;
  onFocusStateChange: (state: LoginFocusState) => void;
  onLoginResultChange: (state: LoginResultState) => void;
  onPasswordVisibilityChange: (visible: boolean) => void;
}

interface LoginValues {
  userName: string;
  userPassword: string;
}

function waitForMotion(duration: number, signal: AbortSignal) {
  if (signal.aborted || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    return Promise.resolve();
  }

  return new Promise<void>((resolve) => {
    const finish = () => {
      window.clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const timer = window.setTimeout(finish, duration);
    signal.addEventListener("abort", finish, { once: true });
  });
}

export default function LoginPanel({
  onAuthenticated,
  onFocusStateChange,
  onLoginResultChange,
  onPasswordVisibilityChange,
}: LoginPanelProps) {
  const [errors, setErrors] = useState<Partial<Record<keyof LoginValues, string>>>({});
  const [loading, setLoading] = useState(false);
  const submissionRef = useRef<AbortController | null>(null);
  const usernameRef = useRef<HTMLInputElement | null>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    return () => {
      // Invalidate UI continuations; this does not cancel the authentication request.
      submissionRef.current?.abort();
      submissionRef.current = null;
    };
  }, []);

  const handleAccountLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionRef.current) return;

    // Read the native values at submission, including silent browser/password-manager autofill.
    const userName = usernameRef.current?.value.trim() ?? "";
    const userPassword = passwordRef.current?.value ?? "";
    const nextErrors: Partial<Record<keyof LoginValues, string>> = {};
    if (!userName) nextErrors.userName = "请输入用户名";
    if (!userPassword) nextErrors.userPassword = "请输入密码";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      const firstInvalidInput = nextErrors.userName ? usernameRef.current : passwordRef.current;
      firstInvalidInput?.focus();
      return;
    }

    const submission = new AbortController();
    submissionRef.current = submission;
    let loginSucceeded = false;

    try {
      setLoading(true);
      onLoginResultChange("submitting");

      await login({
        userName,
        userPassword,
      });

      if (submission.signal.aborted) return;
      loginSucceeded = true;
      onLoginResultChange("success");
      await waitForMotion(LOGIN_SUCCESS_MOTION_MS, submission.signal);
      if (submission.signal.aborted) return;
      await onAuthenticated();

      notifyOnce("login-success", {
        type: "success",
        title: "登录成功！",
        description: "正在进入 Yak Ops",
        meta: "身份验证完成",
        duration: 2,
      });
    } catch {
      if (submission.signal.aborted) return;
      if (!loginSucceeded) {
        onLoginResultChange("failure");
        await waitForMotion(LOGIN_FAILURE_MOTION_MS, submission.signal);
        if (submission.signal.aborted) return;
        onLoginResultChange("idle");
      } else {
        onLoginResultChange("idle");
      }
      // Global request handling surfaces HTTP, business and network failures once.
    } finally {
      if (submissionRef.current === submission) {
        submissionRef.current = null;
        setLoading(false);
      }
    }
  };

  return (
    <>
      <form
        noValidate
        aria-label="账号登录"
        aria-busy={loading}
        onSubmit={(event) => void handleAccountLogin(event)}
      >
        <Field name="userName" invalid={Boolean(errors.userName)} className="!gap-0">
          <FloatingLabelField htmlFor="login-username" label="用户名">
            <Input
              ref={usernameRef}
              id="login-username"
              name="userName"
              size="large"
              variant="underlined"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              readOnly={loading}
              defaultValue=""
              aria-required="true"
              aria-invalid={Boolean(errors.userName) || undefined}
              aria-describedby={errors.userName ? "login-username-error" : undefined}
              onFocus={() => onFocusStateChange("userName")}
              onBlur={() => onFocusStateChange("idle")}
              onChange={() => {
                if (errors.userName) {
                  setErrors((current) => ({ ...current, userName: undefined }));
                }
              }}
            />
          </FloatingLabelField>
          <div className="grid min-h-5 items-start pt-1" aria-live="polite" aria-atomic="true">
            <FieldError id="login-username-error" match={Boolean(errors.userName)}>
              {errors.userName}
            </FieldError>
          </div>
        </Field>

        <Field
          name="userPassword"
          invalid={Boolean(errors.userPassword)}
          className="mt-1 !gap-0"
          onFocus={() => onFocusStateChange("userPassword")}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) onFocusStateChange("idle");
          }}
        >
          <FloatingLabelField htmlFor="login-password" label="密码">
            <PasswordInput
              ref={passwordRef}
              id="login-password"
              name="userPassword"
              size="large"
              variant="underlined"
              autoComplete="current-password"
              showPasswordLabel="显示密码"
              hidePasswordLabel="隐藏密码"
              onVisibilityChange={onPasswordVisibilityChange}
              renderVisibilityIcon={(visible) => <LoginPasswordEye visible={visible} />}
              readOnly={loading}
              defaultValue=""
              aria-required="true"
              aria-invalid={Boolean(errors.userPassword) || undefined}
              aria-describedby={errors.userPassword ? "login-password-error" : undefined}
              onChange={() => {
                if (errors.userPassword) {
                  setErrors((current) => ({
                    ...current,
                    userPassword: undefined,
                  }));
                }
              }}
            />
          </FloatingLabelField>
          <div className="grid min-h-5 items-start pt-1" aria-live="polite" aria-atomic="true">
            <FieldError id="login-password-error" match={Boolean(errors.userPassword)}>
              {errors.userPassword}
            </FieldError>
          </div>
        </Field>

        <Button
          variant="primary"
          size="lg"
          type="submit"
          isPending={loading}
          className="login-form-submit mt-6 w-full"
          style={{ borderRadius: "var(--yak-radius-full)" }}
        >
          登录
        </Button>
      </form>
      {/* Outside the busy form so the status can be announced while it is submitting. */}
      <p className="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {loading ? "正在登录，请稍候。" : ""}
      </p>
    </>
  );
}
