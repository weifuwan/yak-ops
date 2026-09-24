import type { RequestConfig, RunTimeLayoutConfig } from "@umijs/max";
import { getLocale, history, useModel } from "@umijs/max";
import "@ant-design/v5-patch-for-react-19";
import { useEffect } from "react";

import { getCurrentUser } from "./services/security/account";
import { toCurrentUser } from "./services/security/currentIdentity";
import { AUTHENTICATION_INVALIDATED_EVENT } from "./utils/security/authentication";
import {
  getCurrentReturnTo,
  getSafeReturnTo,
  isLoginPath,
} from "./utils/security/redirect";

const loginPath = "/login";

const syncDocumentLocale = () => {
  const currentLocale = getLocale();
  const locale = currentLocale.toLowerCase().startsWith("zh") ? "zh-CN" : "en-US";
  document.documentElement.dataset.yakLocale = locale;
  document.documentElement.lang = locale;
};

export const request: RequestConfig = {};

const redirectAnonymousUser = () => {
  if (isLoginPath(window.location.pathname)) return;
  const returnTo = getCurrentReturnTo();
  history.replace(`${loginPath}?returnTo=${encodeURIComponent(returnTo)}`);
};

const AuthenticationStateSync = () => {
  const { setInitialState } = useModel("@@initialState");

  useEffect(() => {
    const clearAuthenticationState = () => {
      void setInitialState((state) => ({
        ...state,
        currentUser: undefined,
        currentUserLoadError: false,
      }));
    };

    window.addEventListener(AUTHENTICATION_INVALIDATED_EVENT, clearAuthenticationState);
    return () => {
      window.removeEventListener(AUTHENTICATION_INVALIDATED_EVENT, clearAuthenticationState);
    };
  }, [setInitialState]);

  return null;
};

export async function getInitialState(): Promise<{
  currentUser?: API.CurrentUser;
  currentUserLoadError?: boolean;
  fetchUserInfo?: () => Promise<API.CurrentUser | undefined>;
}> {
  syncDocumentLocale();

  const fetchUserInfo = async () => toCurrentUser(await getCurrentUser());
  const onLoginPage = isLoginPath(window.location.pathname);

  let currentUser: API.CurrentUser | undefined;
  let currentUserLoadError = false;

  try {
    currentUser = toCurrentUser(await getCurrentUser({ skipErrorHandler: true }));
  } catch {
    currentUserLoadError = true;
    redirectAnonymousUser();
  }

  if (currentUser && onLoginPage) {
    const requested = new URLSearchParams(window.location.search).get("returnTo");
    history.replace(getSafeReturnTo(requested));
  }

  return {
    fetchUserInfo,
    currentUser,
    currentUserLoadError,
  };
}

export const layout: RunTimeLayoutConfig = ({ initialState }) => ({
  onPageChange: () => {
    if (
      !initialState?.currentUser &&
      !initialState?.currentUserLoadError &&
      !isLoginPath(history.location.pathname)
    ) {
      redirectAnonymousUser();
    }
  },
  childrenRender: (children) => (
    <>
      <AuthenticationStateSync />
      {children}
    </>
  ),
});
