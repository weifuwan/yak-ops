import { useEffect, useRef } from "react";

import "./login-password-eye.css";

interface LoginPasswordEyeProps {
  visible: boolean;
}

/** 登录页的装饰图形；不读取输入框或凭证。按钮行为仍归 PasswordInput。 */
export default function LoginPasswordEye({ visible }: LoginPasswordEyeProps) {
  const eyeRef = useRef<SVGSVGElement | null>(null);

  useEffect(() => {
    const eye = eyeRef.current;
    if (!eye || visible) return;

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const hoverPointer = window.matchMedia("(hover: hover) and (pointer: fine)");
    let frame = 0;
    let tracking = false;
    let pointerX = 0;
    let pointerY = 0;

    const reset = () => {
      window.cancelAnimationFrame(frame);
      frame = 0;
      eye.style.setProperty("--login-eye-x", "0px");
      eye.style.setProperty("--login-eye-y", "0px");
    };

    const draw = () => {
      frame = 0;
      if (!tracking) return;
      const bounds = eye.getBoundingClientRect();
      if (!bounds.width || !bounds.height) {
        reset();
        return;
      }
      const x = (pointerX - bounds.left - bounds.width / 2) / 180;
      const y = (pointerY - bounds.top - bounds.height / 2) / 180;
      const length = Math.max(1, Math.hypot(x, y));
      // Bounded ellipse: the pupil remains inside the fixed eye outline.
      eye.style.setProperty("--login-eye-x", `${(x / length) * 2.2}px`);
      eye.style.setProperty("--login-eye-y", `${(y / length) * 1.4}px`);
    };

    const onPointerMove = (event: PointerEvent) => {
      if (event.pointerType !== "mouse") {
        reset();
        return;
      }
      pointerX = event.clientX;
      pointerY = event.clientY;
      if (!frame) frame = window.requestAnimationFrame(draw);
    };

    const syncTracking = () => {
      window.removeEventListener("pointermove", onPointerMove);
      tracking = !reducedMotion.matches && hoverPointer.matches && !document.hidden;
      reset();
      if (tracking) window.addEventListener("pointermove", onPointerMove, { passive: true });
    };

    syncTracking();
    reducedMotion.addEventListener("change", syncTracking);
    hoverPointer.addEventListener("change", syncTracking);
    document.addEventListener("visibilitychange", syncTracking);
    document.addEventListener("pointerleave", reset);
    window.addEventListener("blur", reset);
    window.addEventListener("resize", reset);
    window.addEventListener("scroll", reset, true);

    return () => {
      tracking = false;
      reset();
      window.removeEventListener("pointermove", onPointerMove);
      reducedMotion.removeEventListener("change", syncTracking);
      hoverPointer.removeEventListener("change", syncTracking);
      document.removeEventListener("visibilitychange", syncTracking);
      document.removeEventListener("pointerleave", reset);
      window.removeEventListener("blur", reset);
      window.removeEventListener("resize", reset);
      window.removeEventListener("scroll", reset, true);
    };
  }, [visible]);

  return (
    <svg
      ref={eyeRef}
      aria-hidden="true"
      focusable="false"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="login-password-eye size-5 shrink-0"
      data-visible={visible}
    >
      <g className="login-password-eye__open">
        <path d="M3 12C5 8.5 8 6.5 12 6.5S19 8.5 21 12c-2 3.5-5 5.5-9 5.5S5 15.5 3 12Z" />
        <g className="login-password-eye__pupil">
          <circle cx="12" cy="12" r="2.2" fill="currentColor" stroke="none" />
        </g>
      </g>
      <g className="login-password-eye__closed">
        <path d="M3 10.5c4 5.5 14 5.5 18 0M5.5 13l-1.3 2M9.5 14.3 9 16.6M14.5 14.3l.5 2.3M18.5 13l1.3 2" />
      </g>
    </svg>
  );
}
