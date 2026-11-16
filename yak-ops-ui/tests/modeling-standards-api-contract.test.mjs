import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const modelingHttp = read("../apps/web/service/data-modeling/shared/modeling-http.ts");
const standardsPath = "../apps/web/service/data-modeling/standards";
const controllersPath = "../../yak-ops-api/src/main/java/io/yak/ops/api/controller/modeling";

const resources = [
  {
    key: "standardDirectory",
    path: "standard-directories",
    service: `${standardsPath}/shared/standard-directory.ts`,
    controller: "StandardDirectoryController.java",
  },
  {
    key: "fieldStandard",
    path: "field-standards",
    service: `${standardsPath}/field-standard.ts`,
    controller: "FieldStandardController.java",
  },
  {
    key: "codeStandard",
    path: "code-standards",
    service: `${standardsPath}/code-standard.ts`,
    controller: "CodeStandardController.java",
  },
  {
    key: "unitStandard",
    path: "measurement-units",
    service: `${standardsPath}/unit-standard.ts`,
    controller: "MeasurementUnitController.java",
  },
  {
    key: "dictionaryStandard",
    path: "naming-dictionaries",
    service: `${standardsPath}/naming-dictionary.ts`,
    controller: "NamingDictionaryController.java",
  },
];

test("data-standard resource paths match the modeling controllers", () => {
  for (const resource of resources) {
    assert.match(
      modelingHttp,
      new RegExp(`${resource.key}: "${resource.path}"`),
      `${resource.key} should use /${resource.path}`,
    );
  }

  assert.doesNotMatch(modelingHttp, /assets\/(standard-directory|field-standard|dictionary-standard)/);
});

test("all data-standard resources use list and ID-addressed update endpoints", () => {
  for (const resource of resources) {
    const service = read(resource.service);
    assert.match(service, /listEndpoint: "list"/, `${resource.key} should query /list`);
    assert.match(service, /updateWithId: true/, `${resource.key} should update /{id}`);
  }
});

test("data-standard controller CRUD mappings match the frontend contract", () => {
  for (const resource of resources) {
    const controller = read(`${controllersPath}/${resource.controller}`);
    assert.match(
      controller,
      new RegExp(`@RequestMapping\\("/projects/\\{projectCode\\}/modeling/${resource.path}"\\)`),
      `${resource.controller} should expose /${resource.path}`,
    );
    assert.match(controller, /@PostMapping\("\/page"\)/);
    assert.match(controller, /@GetMapping\("\/list"\)/);
    assert.match(controller, /@GetMapping\("\/\{id\}"\)/);
    assert.match(controller, /@PutMapping\("\/\{id\}"\)/);
    assert.match(controller, /@DeleteMapping\("\/\{id\}"\)/);
  }
});

test("standard directory tree uses the shared standard-directories endpoint", () => {
  const directoryService = read(resources[0].service);
  const directoryController = read(`${controllersPath}/StandardDirectoryController.java`);

  assert.match(directoryService, /modelingResourcePath\(projectCode, "standardDirectory"\).*\/tree\?module=/s);
  assert.match(directoryController, /@GetMapping\("\/tree"\)/);
  assert.match(directoryController, /@RequestParam\("module"\)/);
});
