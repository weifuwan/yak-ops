import {
  Button,
  Input,
  PasswordInput,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import { AlertCircle } from "lucide-react";
import {
  useRef,
  useState,
  type ChangeEvent,
  type FocusEvent,
  type FormEvent,
  type InputHTMLAttributes,
} from "react";

import { notifyOnce } from "@/utils/notification";
import { login } from "../../service/auth";

const WECHAT_QR_CODE_SRC = "/wechat_qr.png";

interface LoginPanelProps {
  onAuthenticated: () => Promise<void>;
}

interface LoginValues {
  userName: string;
  userPassword: string;
}

type FloatingInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "className" | "size" | "type" | "value" | "onChange"
> & {
  label: string;
  password?: boolean;
  value: string;
  invalid?: boolean;
  onValueChange: (value: string) => void;
};

function FloatingInput({
  label,
  password = false,
  value,
  invalid = false,
  onValueChange,
  onBlur,
  onFocus,
  ...inputProps
}: FloatingInputProps) {
  const [focused, setFocused] = useState(false);
  const floating = focused || value.length > 0;

  const className = [
    "h-11 rounded-full border bg-white px-4 text-[15px] shadow-none",
    invalid
      ? "border-[#d92d20] hover:border-[#d92d20] focus:border-[#d92d20]"
      : "border-[#dededb] hover:border-[#bdbdb8] focus:border-[#171717]",
  ].join(" ");

  const sharedProps = {
    ...inputProps,
    value,
    className,
    placeholder: "",
    "aria-invalid": invalid || undefined,
    onFocus: (event: FocusEvent<HTMLInputElement>) => {
      setFocused(true);
      onFocus?.(event);
    },
    onBlur: (event: FocusEvent<HTMLInputElement>) => {
      setFocused(false);
      onBlur?.(event);
    },
    onChange: (event: ChangeEvent<HTMLInputElement>) =>
      onValueChange(event.target.value),
  };

  return (
    <div className="relative">
      {password ? (
        <PasswordInput {...sharedProps} />
      ) : (
        <Input {...sharedProps} />
      )}
      <label
        htmlFor={inputProps.id}
        className={`pointer-events-none absolute left-4 z-10 bg-white px-1 transition-all duration-200 ease-out ${
          floating
            ? "top-0 -translate-y-1/2 text-[12px] font-medium text-[#333]"
            : "top-1/2 -translate-y-1/2 text-[15px] text-[#aaa]"
        }`}
      >
        {label}
      </label>
    </div>
  );
}

function ValidationMessage({ children }: { children: string }) {
  return (
    <span className="mt-1.5 inline-flex min-h-[18px] items-center gap-1.5 text-[12px] leading-[18px] text-[#b42318]">
      <AlertCircle size={12} className="shrink-0" />
      <span>{children}</span>
    </span>
  );
}

function WeChatQrHelp() {
  const [qrCodeAvailable, setQrCodeAvailable] = useState(true);
  const [open, setOpen] = useState(false);
  const closeTimerRef = useRef<number>();

  const openPopover = () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    setOpen(true);
  };

  const scheduleClose = () => {
    closeTimerRef.current = window.setTimeout(() => setOpen(false), 120);
  };

  return (
    <div className="mt-3 text-center text-[11px] leading-5 text-[#8c8c88]">
      获取账号 / 密码，请扫描{" "}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          type="button"
          className="cursor-help border-0 bg-transparent p-0 font-medium text-[#555] underline decoration-[#d6d6d1] underline-offset-2 transition-colors hover:text-[#171717]"
          onMouseEnter={openPopover}
          onMouseLeave={scheduleClose}
        >
          微信公众号二维码
        </PopoverTrigger>
        <PopoverContent
          side="right"
          className="w-[188px] p-2"
          onMouseEnter={openPopover}
          onMouseLeave={scheduleClose}
        >
          <div className="flex flex-col items-center gap-2">
            {qrCodeAvailable ? (
              <img
                src={WECHAT_QR_CODE_SRC}
                alt="微信公众号二维码"
                className="h-40 w-40 rounded-xl object-cover"
                onError={() => setQrCodeAvailable(false)}
              />
            ) : (
              <div className="flex h-40 w-40 items-center justify-center rounded-xl border border-dashed border-[#dededb] bg-[#fafafa] px-5 text-center text-[12px] leading-5 text-[#999]">
                微信公众号二维码待上传
              </div>
            )}
            <span className="text-center text-[11px] leading-5 text-[#888]">
              输入{" "}
              <span className="rounded bg-black/[0.03] px-1">9527</span>{" "}
              获取账号 / 密码
            </span>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default function LoginPanel({ onAuthenticated }: LoginPanelProps) {
  const [values, setValues] = useState<LoginValues>({
    userName: "",
    userPassword: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof LoginValues, string>>>(
    {},
  );
  const [loading, setLoading] = useState(false);

  const handleAccountLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const nextErrors: Partial<Record<keyof LoginValues, string>> = {};
    if (!values.userName.trim()) nextErrors.userName = "请输入用户名";
    if (!values.userPassword) nextErrors.userPassword = "请输入密码";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    try {
      setLoading(true);
      await login({
        userName: values.userName.trim(),
        pw: values.userPassword,
      });
      await onAuthenticated();

      notifyOnce("login-success", {
        type: "success",
        title: "登录成功！",
        description: "正在进入 Yak Ops",
        meta: "身份验证完成",
        duration: 2,
      });
    } catch {
      // Global request handling surfaces HTTP, business and network failures once.
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full rounded-[26px] border border-[#e4e4e1] bg-white p-6 shadow-[0_14px_40px_rgba(15,23,42,0.04)] sm:p-7">
      <form noValidate onSubmit={(event) => void handleAccountLogin(event)}>
        <div className="mb-5">
          <FloatingInput
            id="login-username"
            label="Username"
            autoComplete="username"
            value={values.userName}
            invalid={Boolean(errors.userName)}
            onValueChange={(userName) => {
              setValues((current) => ({ ...current, userName }));
              if (errors.userName) {
                setErrors((current) => ({ ...current, userName: undefined }));
              }
            }}
          />
          {errors.userName ? (
            <ValidationMessage>{errors.userName}</ValidationMessage>
          ) : null}
        </div>

        <div className="mb-5">
          <FloatingInput
            id="login-password"
            label="Password"
            password
            autoComplete="current-password"
            value={values.userPassword}
            invalid={Boolean(errors.userPassword)}
            onValueChange={(userPassword) => {
              setValues((current) => ({ ...current, userPassword }));
              if (errors.userPassword) {
                setErrors((current) => ({
                  ...current,
                  userPassword: undefined,
                }));
              }
            }}
          />
          {errors.userPassword ? (
            <ValidationMessage>{errors.userPassword}</ValidationMessage>
          ) : null}
        </div>

        <Button
          variant="primary"
          size="large"
          type="submit"
          loading={loading}
          className="h-11 w-full rounded-full"
        >
          Log in
        </Button>

        <WeChatQrHelp />
      </form>
    </div>
  );
}
