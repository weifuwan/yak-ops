import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const navigation = read("../apps/web/app/layout/navigation.ts");
const router = read("../apps/web/app/router/AppRouter.tsx");
const page = read("../apps/web/app/management/tokens/page.tsx");
const form = read("../apps/web/app/management/tokens/token-form.tsx");
const table = read("../apps/web/app/management/tokens/token-table.tsx");
const service = read("../apps/web/service/access-token/index.ts");

test("Token management is registered below user management in the Management Center", () => {
  const userPathIndex = navigation.indexOf('path: "/management/users"');
  const tokenPathIndex = navigation.indexOf('path: "/management/tokens"');
  assert.ok(userPathIndex >= 0);
  assert.ok(tokenPathIndex > userPathIndex);
  assert.match(navigation, /label: "Token 管理"[\s\S]*?icon: KeyRound/);
  assert.match(router, /path="\/management\/tokens" element={<AccessTokenManagementPage \/>}/);
  assert.match(router, /"\/management\/tokens"/);
});

test("Token page uses the existing access-token API contract", () => {
  assert.match(service, /YAK_OPS_API_PREFIX \+ "\/access-tokens"/);
  assert.match(service, /HttpUtils\.getData/);
  assert.match(service, /HttpUtils\.postData/);
  assert.match(service, /HttpUtils\.putData/);
  assert.match(service, /HttpUtils\.deleteData/);
  assert.match(service, /pageNo/);
  assert.match(service, /pageSize/);
  assert.match(service, /searchVal/);
  assert.match(page, /listAccessTokens/);
  assert.match(page, /deleteAccessToken/);
  assert.match(form, /createAccessToken/);
  assert.match(form, /updateAccessToken/);
});

test("Token creation and renewal make credential rotation explicit", () => {
  assert.match(service, /Omitting token asks the existing backend endpoint to generate and persist it/);
  assert.match(service, /generates a replacement token when the optional token parameter is omitted/);
  assert.match(form, /保存后会生成新的 Token，原 Token 将立即失效/);
  assert.match(form, /创建成功后请复制并妥善保存/);
  assert.match(page, /Token 创建成功/);
  assert.match(page, /Token 已更新/);
});

test("Token values are masked in the table and copied only on explicit action", () => {
  assert.match(table, /maskToken/);
  assert.match(table, /••••••••/);
  assert.match(table, /onCopy\(record\.token\)/);
  assert.match(page, /navigator\.clipboard\.writeText/);
});

test("Token form uses the authenticated user by default and lets admins choose a user", () => {
  assert.match(form, /currentUserId/);
  assert.match(form, /currentUser\?\.userType === "ADMIN_USER"/);
  assert.match(form, /listAllUsers/);
  assert.match(form, /type="datetime-local"/);
  assert.match(form, /有效期必须晚于当前时间/);
});
