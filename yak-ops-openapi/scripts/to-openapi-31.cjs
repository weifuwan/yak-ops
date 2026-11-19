#!/usr/bin/env node
/**
 * Upgrade a springdoc-generated OpenAPI 3.0.x document to OpenAPI 3.1.0.
 *
 * The current stack (springdoc 1.6.9 / swagger-core 2.2.0 on Spring Boot 2.x) can only emit
 * OpenAPI 3.0.1, so the spec is exported first and mechanically upgraded here:
 *
 *   curl -s http://127.0.0.1:12345/yak-ops/v3/api-docs -o /tmp/api-docs.json
 *   node scripts/openapi/to-openapi-31.cjs /tmp/api-docs.json docs/openapi/yak-ops-3.1.json
 *
 * Handled 3.0 -> 3.1 differences (no-ops today, kept for future annotations):
 *   - nullable: true          -> type: [<type>, "null"]
 *   - exclusiveMinimum/Maximum: true + minimum/maximum -> numeric exclusiveMinimum/Maximum
 *   - example: x              -> examples: [x]          (Schema Object only)
 *   - type: file              -> contentMediaType: application/octet-stream
 *
 * Usage: node scripts/openapi/to-openapi-31.cjs <input.json|-> [output.json]
 */
const fs = require("fs");
const path = require("path");

const stats = {
  nullable: 0, nullableUnhandled: 0, exclusive: 0, example: 0, fileType: 0,
  schemas: 0, security: 0, tokenAuth: 0, tagsNormalized: 0, tagsUnmapped: 0,
  summariesDerived: 0, summaryOverridden: 0, summaryOverrideMisses: 0, serverUrl: 0,
};

/**
 * Tag normalization map: three legacy styles (CONST_TAG, "xxx related operation",
 * "xxx-controller") collapse into business-domain kebab-case. Unmapped tags are
 * kept as-is and flagged with x-agent-unmapped-tag for later governance.
 */
const TAG_MAP = {
  ALERT_TAG: "alert",
  DATA_MODELING_TAG: "data-modeling",
  DYNAMIC_TASK_TYPE: "dynamic-task-type",
  PROJECT_WORKER_GROUP_TAG: "project-worker-group",
  TASK_PLUGIN: "task-plugin",
  USER_PREFERENCE_TAG: "user-preference",
  "UI plugin related operation": "ui-plugin",
  "User login related operations": "users",
  "access token related operation": "access-token",
  "agent-chat-controller": "agent-chat",
  "alert group related operation": "alert",
  "alert plugin instance related operation": "alert",
  "analysis related operation of task state": "task-analysis",
  "atom-indicator-controller": "atom-indicator",
  "audit log related operation": "audit-log",
  "business-process-controller": "business-process",
  "business-type-controller": "business-type",
  "cluster related operation": "cluster",
  "code-standard-controller": "code-standard",
  "composite-indicator-controller": "composite-indicator",
  "data source related operation": "data-source",
  "data-domain-controller": "data-domain",
  "data-mart-controller": "data-mart",
  "data-source-connector-controller": "data-source-connector",
  "data-sync-controller": "data-sync",
  "derived-indicator-controller": "derived-indicator",
  "dimension-modeling-controller": "dimension-modeling",
  "dw-layer-controller": "dw-layer",
  "environment related operation": "environment",
  "executor operation": "executor",
  "favourite related operation": "favourite",
  "field-standard-controller": "field-standard",
  "log related operation": "log",
  "measurement-unit-controller": "measurement-unit",
  "modifier-controller": "modifier",
  "monitor related operation": "monitor",
  "naming-dictionary-controller": "naming-dictionary",
  "process instance related operation": "process-instance",
  "project parameter related operation": "project-parameter",
  "project preference related operation": "project-preference",
  "project related operation": "project",
  "queue related operation": "queue",
  "resource center related operation": "resource",
  "schedule related operation": "schedule",
  "standard-directory-controller": "standard-directory",
  "subject-domain-controller": "subject-domain",
  "task definition related operation": "task-definition",
  "task group related operation": "task-group",
  "task instance related operation": "task-instance",
  "tenant related operation": "tenant",
  "time-period-controller": "time-period",
  "users related operation": "users",
  "work flow lineage related operation": "workflow-lineage",
  "worker group related operation": "worker-group",
  "workflow definition related operation": "workflow-definition",
};

const NORMALIZED_TAGS = new Set(Object.values(TAG_MAP));

function normalizeTag(tag) {
  if (NORMALIZED_TAGS.has(tag)) return tag; // already a normalized target (idempotent re-runs)
  if (TAG_MAP[tag]) return TAG_MAP[tag];
  let domain = tag;
  if (domain.endsWith("-controller")) domain = domain.slice(0, -"-controller".length);
  else if (domain.endsWith(" related operations")) domain = domain.slice(0, -" related operations".length);
  else if (domain.endsWith(" related operation")) domain = domain.slice(0, -" related operation".length);
  else if (domain.endsWith("_TAG")) domain = domain.slice(0, -"_TAG".length);
  else return null;
  return domain.toLowerCase().replace(/[\s_]+/g, "-");
}

function applyTagNormalization(doc) {
  for (const item of Object.values(doc.paths || {})) {
    for (const operation of Object.values(item)) {
      if (!operation || !operation.tags) continue;
      const normalized = [];
      for (const tag of operation.tags) {
        const target = normalizeTag(tag);
        if (target === null) {
          if (!operation["x-agent-unmapped-tag"]) operation["x-agent-unmapped-tag"] = [];
          operation["x-agent-unmapped-tag"].push(tag);
          stats.tagsUnmapped += 1;
          normalized.push(tag);
        } else {
          if (target !== tag) stats.tagsNormalized += 1;
          if (!normalized.includes(target)) normalized.push(target);
        }
      }
      operation.tags = normalized;
    }
  }
}

function applySummaryFallback(doc) {
  for (const item of Object.values(doc.paths || {})) {
    for (const operation of Object.values(item)) {
      if (!operation || operation.summary) continue;
      const fromId = (operation.operationId || "")
        .replace(/([a-z0-9])([A-Z])/g, "$1 $2")
        .replace(/[-_]+/g, " ")
        .trim();
      if (!fromId) continue;
      operation.summary = fromId.charAt(0).toUpperCase() + fromId.slice(1);
      operation["x-agent-summary-derived"] = true;
      stats.summariesDerived += 1;
    }
  }
}

/**
 * Apply hand-written summaries from summary-overrides.cjs (highest priority).
 * Runs after the derived fallback so overrides also replace derived text.
 */
function applySummaryOverrides(doc) {
  let overrides;
  try {
    overrides = require("./summary-overrides.cjs");
  } catch (error) {
    console.warn(`summary-overrides.cjs not loaded: ${error.message}`);
    return;
  }
  const known = new Set(Object.keys(overrides));
  for (const item of Object.values(doc.paths || {})) {
    for (const operation of Object.values(item)) {
      if (!operation || !operation.operationId) continue;
      const override = overrides[operation.operationId];
      if (!override) continue;
      known.delete(operation.operationId);
      if (operation.summary !== override.summary) stats.summaryOverridden += 1;
      operation.summary = override.summary;
      if (override.description) operation.description = override.description;
      delete operation["x-agent-summary-derived"];
    }
  }
  if (known.size > 0) {
    stats.summaryOverrideMisses += known.size;
    console.warn(`summary-overrides without a matching operation: ${[...known].join(", ")}`);
  }
}

function applyServers(doc, serverUrl) {
  if (!serverUrl) return;
  doc.servers = [{ url: serverUrl, description: "yak-ops API base (set at publish time)" }];
  stats.serverUrl += 1;
}


/**
 * Paths the login interceptor excludes (see AppConfiguration). They are reachable without a
 * session, so their operations must declare an empty security requirement.
 */
const PUBLIC_PATH_PATTERNS = [
  /^\/login(\/|$)/,
  /^\/users\/register$/,
  /^\/cookies$/,
  /^\/oidc-providers$/,
  /^\/redirect\/login\/oauth2$/,
  /^\/oauth2\/authorization\//,
];

const SESSION_COOKIE = "sessionId";

function applySecurityModel(doc) {
  doc.components = doc.components || {};
  doc.components.securitySchemes = doc.components.securitySchemes || {};
  if (!doc.components.securitySchemes.cookieAuth) {
    doc.components.securitySchemes.cookieAuth = {
      type: "apiKey",
      in: "cookie",
      name: SESSION_COOKIE,
      description:
        "登录会话 Cookie（Constants.SESSION_ID = \"sessionId\"）。先调用 POST /login 建立会话，后续请求自动携带。",
    };
  }
  if (!doc.components.securitySchemes.tokenAuth) {
    doc.components.securitySchemes.tokenAuth = {
      type: "apiKey",
      in: "header",
      name: "token",
      description:
        "平台 API Token 请求头（LoginHandlerInterceptor 校验 t_yak_ops_access_token，含过期时间）。经 /access-tokens 创建。",
    };
    stats.tokenAuth += 1;
  }
  // OR semantics: cookie session or token header, matching the interceptor.
  doc.security = [{ cookieAuth: [] }, { tokenAuth: [] }];

  for (const [route, item] of Object.entries(doc.paths || {})) {
    if (!PUBLIC_PATH_PATTERNS.some((pattern) => pattern.test(route))) continue;
    for (const [method, operation] of Object.entries(item)) {
      if (!["get", "post", "put", "patch", "delete", "head", "options", "trace"].includes(method)) continue;
      operation.security = [];
      stats.security += 1;
    }
  }
}


function applySchemaRules(schema) {
  if (schema.nullable === true) {
    if (typeof schema.type === "string") {
      schema.type = [schema.type, "null"];
      delete schema.nullable;
      stats.nullable += 1;
    } else if (Array.isArray(schema.type)) {
      if (!schema.type.includes("null")) schema.type.push("null");
      delete schema.nullable;
      stats.nullable += 1;
    } else {
      // Without a declared type the nullable flag cannot be translated losslessly.
      delete schema.nullable;
      stats.nullableUnhandled += 1;
    }
  }

  if (schema.exclusiveMinimum === true && typeof schema.minimum === "number") {
    schema.exclusiveMinimum = schema.minimum;
    delete schema.minimum;
    stats.exclusive += 1;
  }
  if (schema.exclusiveMaximum === true && typeof schema.maximum === "number") {
    schema.exclusiveMaximum = schema.maximum;
    delete schema.maximum;
    stats.exclusive += 1;
  }

  if (Object.prototype.hasOwnProperty.call(schema, "example")) {
    if (!Array.isArray(schema.examples)) {
      schema.examples = [schema.example];
    }
    delete schema.example;
    stats.example += 1;
  }

  if (schema.type === "file") {
    delete schema.type;
    schema.contentMediaType = schema.contentMediaType || "application/octet-stream";
    stats.fileType += 1;
  }
}

function walkSchema(schema) {
  if (!schema || typeof schema !== "object") return;
  if (Array.isArray(schema)) {
    schema.forEach(walkSchema);
    return;
  }
  stats.schemas += 1;
  applySchemaRules(schema);

  for (const key of ["properties", "patternProperties", "dependentSchemas"]) {
    if (schema[key] && typeof schema[key] === "object") {
      Object.values(schema[key]).forEach(walkSchema);
    }
  }
  for (const key of ["items", "additionalProperties", "not", "contains", "propertyNames", "if", "then", "else"]) {
    if (schema[key]) walkSchema(schema[key]);
  }
  for (const key of ["allOf", "anyOf", "oneOf", "prefixItems"]) {
    if (Array.isArray(schema[key])) schema[key].forEach(walkSchema);
  }
  if (schema.additionalProperties && typeof schema.additionalProperties === "object") {
    walkSchema(schema.additionalProperties);
  }
}

/** Collect every object that lives under a `schema` key, plus component schemas. */
function collectSchemaNodes(node, out) {
  if (!node || typeof node !== "object") return;
  if (Array.isArray(node)) {
    node.forEach((item) => collectSchemaNodes(item, out));
    return;
  }
  for (const [key, value] of Object.entries(node)) {
    if (key === "schema") {
      out.push(value);
      continue;
    }
    collectSchemaNodes(value, out);
  }
}

function upgrade(doc, options = {}) {
  const sourceVersion = doc.openapi || "unknown";
  if (String(sourceVersion).startsWith("3.0")) {
    doc.openapi = "3.1.0";
    if (!doc.jsonSchemaDialect) {
      doc.jsonSchemaDialect = "https://json-schema.org/draft/2020-12/schema";
    }

    const componentSchemas = (doc.components && doc.components.schemas) || {};
    Object.values(componentSchemas).forEach(walkSchema);

    const operationSchemas = [];
    collectSchemaNodes(doc.paths || {}, operationSchemas);
    operationSchemas.forEach(walkSchema);

    if (doc.components && doc.components.parameters) {
      const parameterSchemas = [];
      collectSchemaNodes(doc.components.parameters, parameterSchemas);
      parameterSchemas.forEach(walkSchema);
    }
    if (doc.components && doc.components.responses) {
      const responseSchemas = [];
      collectSchemaNodes(doc.components.responses, responseSchemas);
      responseSchemas.forEach(walkSchema);
    }
  } else if (sourceVersion !== "3.1.0") {
    throw new Error(`expected an OpenAPI 3.0.x or 3.1.0 document, got ${sourceVersion}`);
  }

  // Governance is idempotent and runs for both 3.0 conversions and 3.1 re-runs.
  applySecurityModel(doc);
  applyTagNormalization(doc);
  applySummaryFallback(doc);
  applySummaryOverrides(doc);
  applyServers(doc, options.serverUrl);

  return { sourceVersion };
}

function validate(doc) {
  const problems = [];
  if (doc.openapi !== "3.1.0") problems.push(`openapi must be 3.1.0, got ${doc.openapi}`);
  if (!doc.info || !doc.info.title || !doc.info.version) problems.push("info.title and info.version are required");
  const paths = doc.paths || {};
  if (Object.keys(paths).length === 0) problems.push("paths must not be empty");

  let operations = 0;
  for (const [route, item] of Object.entries(paths)) {
    for (const [method, operation] of Object.entries(item)) {
      if (!["get", "post", "put", "patch", "delete", "head", "options", "trace"].includes(method)) continue;
      operations += 1;
      if (!operation.responses || Object.keys(operation.responses).length === 0) {
        problems.push(`${method.toUpperCase()} ${route} has no responses`);
      }
      if (!operation.operationId) {
        problems.push(`${method.toUpperCase()} ${route} has no operationId`);
      }
    }
  }
  return { problems, operations };
}

function main() {
  const args = process.argv.slice(2);
  const serverUrl = (args.find((arg) => arg.startsWith("--server-url=")) || "").split("=").slice(1).join("=") || "";
  const positional = args.filter((arg) => !arg.startsWith("--"));
  const [inputArg, outputArg] = positional;
  if (!inputArg) {
    console.error(
      "usage: node scripts/openapi/to-openapi-31.cjs <input.json|-> [output.json] [--server-url=URL]",
    );
    process.exit(2);
  }

  const raw = inputArg === "-" ? fs.readFileSync(0, "utf8") : fs.readFileSync(inputArg, "utf8");
  const doc = JSON.parse(raw);
  const { sourceVersion } = upgrade(doc, { serverUrl });

  const { problems, operations } = validate(doc);
  const serialized = JSON.stringify(doc, null, 2) + "\n";

  const output = outputArg || inputArg.replace(/\.json$/, "") + ".3.1.json";
  fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true });
  fs.writeFileSync(output, serialized, "utf8");

  console.log(`source=${sourceVersion} -> openapi=${doc.openapi}`);
  console.log(`operations=${operations} componentSchemas=${Object.keys((doc.components && doc.components.schemas) || {}).length}`);
  console.log(
    `converted: nullable=${stats.nullable} nullableUnhandled=${stats.nullableUnhandled} ` +
      `exclusive=${stats.exclusive} example=${stats.example} fileType=${stats.fileType} ` +
      `publicOperations=${stats.security} visitedSchemas=${stats.schemas}`,
  );
  console.log(
    `governance: tokenAuth=${stats.tokenAuth} tagsNormalized=${stats.tagsNormalized} ` +
      `tagsUnmapped=${stats.tagsUnmapped} summariesDerived=${stats.summariesDerived} ` +
      `summaryOverridden=${stats.summaryOverridden} serverUrl=${stats.serverUrl ? "set" : "kept"}`,
  );
  console.log(`output=${output} bytes=${Buffer.byteLength(serialized, "utf8")}`);

  if (problems.length > 0) {
    console.error(`validationProblems=${problems.length}`);
    problems.slice(0, 10).forEach((problem) => console.error("  - " + problem));
    process.exit(1);
  }
  console.log("validation=ok");
}

main();
