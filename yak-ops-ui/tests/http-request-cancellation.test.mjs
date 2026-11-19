import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import {
  isAbortError,
  isRequestAborted,
} from "../apps/web/service/http/abort.ts";

const requestSource = readFileSync(
  new URL("../apps/web/service/http/request.ts", import.meta.url),
  "utf8",
);

test("AbortError is recognized even when no AbortSignal is available", () => {
  assert.equal(isAbortError(new DOMException("Request aborted", "AbortError")), true);
  assert.equal(isAbortError(Object.assign(new Error("Request aborted"), { name: "AbortError" })), true);
});

test("an aborted signal marks the request as canceled", () => {
  const controller = new AbortController();
  controller.abort();

  assert.equal(
    isRequestAborted(new TypeError("Failed to fetch"), controller.signal),
    true,
  );
});

test("ordinary network errors are not classified as request cancellation", () => {
  assert.equal(isRequestAborted(new TypeError("Failed to fetch")), false);
  assert.equal(isRequestAborted(new Error("Server unavailable")), false);
});

test("the shared HTTP handler suppresses network warnings for canceled requests", () => {
  const catchBlock = requestSource.match(/catch \(error\) \{([\s\S]*?)\n  \}/)?.[1] ?? "";

  assert.match(
    catchBlock,
    /if \(!skipErrorHandler && !isRequestAborted\(error, requestInit\.signal\)\)/,
  );
  assert.match(catchBlock, /notifyOnce\("network"/);
  assert.match(catchBlock, /throw error;/);
});
