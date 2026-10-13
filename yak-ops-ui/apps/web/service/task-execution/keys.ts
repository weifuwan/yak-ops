/** One new key per intentional Run; reuse the same key when retrying that request. */
export const createTaskRunIdempotencyKey = (): string => crypto.randomUUID();
