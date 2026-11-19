export const isAbortError = (error: unknown): boolean =>
  typeof error === "object" &&
  error !== null &&
  "name" in error &&
  error.name === "AbortError";

export const isRequestAborted = (
  error: unknown,
  signal?: AbortSignal | null,
): boolean => signal?.aborted === true || isAbortError(error);
