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

const stats = { nullable: 0, nullableUnhandled: 0, exclusive: 0, example: 0, fileType: 0, schemas: 0, security: 0 };

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
  if (!doc.security) {
    doc.security = [{ cookieAuth: [] }];
  }

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

function upgrade(doc) {
  const sourceVersion = doc.openapi || "unknown";
  if (!String(sourceVersion).startsWith("3.0")) {
    throw new Error(`expected an OpenAPI 3.0.x document, got ${sourceVersion}`);
  }

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

  applySecurityModel(doc);

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
  const [inputArg, outputArg] = process.argv.slice(2);
  if (!inputArg) {
    console.error("usage: node scripts/openapi/to-openapi-31.cjs <input.json|-> [output.json]");
    process.exit(2);
  }

  const raw = inputArg === "-" ? fs.readFileSync(0, "utf8") : fs.readFileSync(inputArg, "utf8");
  const doc = JSON.parse(raw);
  const { sourceVersion } = upgrade(doc);

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
  console.log(`output=${output} bytes=${Buffer.byteLength(serialized, "utf8")}`);

  if (problems.length > 0) {
    console.error(`validationProblems=${problems.length}`);
    problems.slice(0, 10).forEach((problem) => console.error("  - " + problem));
    process.exit(1);
  }
  console.log("validation=ok");
}

main();
