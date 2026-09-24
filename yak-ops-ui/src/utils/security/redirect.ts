const LOGIN_PATH = "/login";
const DEFAULT_AUTHENTICATED_PATH = "/data-source";

export const isLoginPath = (pathname: string): boolean =>
  pathname.toLowerCase() === LOGIN_PATH ||
  pathname.toLowerCase().startsWith(`${LOGIN_PATH}/`);

export const getCurrentReturnTo = (location: Location = window.location) =>
  `${location.pathname}${location.search}${location.hash}`;

export const getSafeReturnTo = (
  requested: string | null | undefined,
  origin: string = window.location.origin,
): string => {
  if (!requested) return DEFAULT_AUTHENTICATED_PATH;

  try {
    const destination = new URL(requested, origin);
    if (
      destination.origin !== origin ||
      isLoginPath(destination.pathname) ||
      destination.pathname !== DEFAULT_AUTHENTICATED_PATH
    ) {
      return DEFAULT_AUTHENTICATED_PATH;
    }
    return `${destination.pathname}${destination.search}${destination.hash}`;
  } catch {
    return DEFAULT_AUTHENTICATED_PATH;
  }
};
