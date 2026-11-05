import assert from "node:assert/strict";
import test from "node:test";

import { YAK_OPS_API_PREFIX, API_PREFIX } from "../apps/web/config/api.ts";
import {
  encodeLoginCredentials,
  toAuthUser,
} from "../apps/web/service/auth/contract.ts";
import {
  API_SUCCESS_CODE,
  isSuccessfulResponse,
  isUnauthenticatedResponse,
} from "../apps/web/service/http/response.ts";

test("Java login endpoints retain the servlet context path", () => {
  assert.equal(YAK_OPS_API_PREFIX, "/yak-ops");
  assert.equal(API_PREFIX, "/api/v1");
});

test("login credentials are form-urlencoded with backend parameter names", async () => {
  const body = encodeLoginCredentials({
    userName: "admin",
    userPassword: "p@ss&+中文字",
  });

  assert.equal(body.get("userName"), "admin");
  assert.equal(body.get("userPassword"), "p@ss&+中文字");
  assert.equal(body.get("pw"), null);

  const request = new Request("http://localhost/yak-ops/login", {
    method: "POST",
    body,
  });
  assert.match(request.headers.get("content-type") ?? "", /^application\/x-www-form-urlencoded/i);
  assert.deepEqual(
    Object.fromEntries(new URLSearchParams(await request.text())),
    { userName: "admin", userPassword: "p@ss&+中文字" },
  );
});

test("backend user IDs and account properties are mapped for the React app", () => {
  const result = toAuthUser({
    id: 42,
    userName: "operator",
    email: null,
    phone: "123456",
    userType: "GENERAL_USER",
    tenantId: 3,
    state: 1,
    timeZone: "Asia/Shanghai",
  });

  assert.equal(result.id, "42");
  assert.equal(result.userid, "42");
  assert.equal(result.name, "operator");
  assert.equal(result.userName, "operator");
  assert.equal(result.email, undefined);
  assert.equal(result.phone, "123456");
  assert.equal(result.tenantId, 3);
  assert.equal(result.userType, "GENERAL_USER");
  assert.equal(result.timeZone, "Asia/Shanghai");
});

test("DolphinScheduler API success is business code 0 rather than HTTP 200", () => {
  assert.equal(API_SUCCESS_CODE, 0);
  assert.equal(isSuccessfulResponse({ code: 0 }), true);
  assert.equal(isSuccessfulResponse({ code: 200 }), false);
  assert.equal(isSuccessfulResponse({ code: 10013 }), false);
});

test("HTTP and business authentication errors trigger session recovery", () => {
  assert.equal(isUnauthenticatedResponse({ code: 401 }), true);
  assert.equal(isUnauthenticatedResponse({ code: 2001 }), true);
  assert.equal(isUnauthenticatedResponse({ code: 10013, msg: "user name or password error" }), false);
});
