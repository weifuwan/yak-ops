import assert from "node:assert/strict";
import {
  canEditFieldMapping,
  fieldProjection,
  mappedSourcePrimaryKeys,
  readDirectProjection,
  replaceTableTransforms,
  sameNameFieldMappings,
  transformEditingError,
  transformTableSelector,
  updateTransformRule,
} from "../apps/web/service/data-sync/transforms.ts";

const source = [
  { name: "id", typeName: "BIGINT", primaryKey: true, nullable: false },
  { name: "tenant", typeName: "INT", primaryKey: true, nullable: false },
  { name: "name", typeName: "VARCHAR", nullable: true },
  { name: "amount", typeName: "DECIMAL", nullable: true },
];
const selector = transformTableSelector({ database: "app", table: "orders" });
assert.equal(selector, "app.orders");
assert.equal(
  transformTableSelector({ database: "app+", table: "order(2026)" }),
  "app\\+.order\\(2026\\)",
);
assert.deepEqual(
  sameNameFieldMappings(source),
  source.map(({ name }) => ({ source: name, target: name })),
);
assert.deepEqual(
  sameNameFieldMappings(source, [{ name: "amount" }, { name: "id" }, { name: "NAME" }]),
  [
    { source: "id", target: "id" },
    { source: "amount", target: "amount" },
  ],
);

const fields = [
  { source: "tenant", target: "tenant" },
  { source: "id", target: "order_id" },
  { source: "name", target: "customer_name" },
];
const projection = fieldProjection(fields);
assert.equal(projection, "`tenant`, `id` AS `order_id`, `name` AS `customer_name`");
assert.deepEqual(readDirectProjection(projection), { all: false, fields });
assert.throws(() => fieldProjection([]), /至少选择一个/);
for (const text of [undefined, "", "  ", "*", " * "])
  assert.deepEqual(readDirectProjection(text), { all: true });

const quotedFields = [
  { source: "select", target: "中文字段" },
  { source: "last,first", target: "quoted`name" },
  { source: "x` AS `y", target: "x` AS `y" },
];
assert.deepEqual(readDirectProjection(fieldProjection(quotedFields)), {
  all: false,
  fields: quotedFields,
});
for (const expression of [
  "id, name",
  "`id` AS `id`",
  "`id`,",
  "`id`, ",
  "`id` ",
  "`id`, `id` AS `copy_id`",
  "`id`, CONCAT(name, ', ', CAST(amount AS STRING)) AS label",
  "CASE WHEN amount > 0 THEN 'a,b' ELSE 'c' END AS label",
  "`id`, /* keep comment */ `name`",
  "CAST(amount AS DECIMAL(10, 2)) AS total",
])
  assert.equal(readDirectProjection(expression), undefined, expression);

assert.deepEqual(mappedSourcePrimaryKeys(fields, source), ["order_id", "tenant"]);
assert.equal(
  mappedSourcePrimaryKeys(
    fields.filter((field) => field.source !== "tenant"),
    source,
  ),
  undefined,
);
assert.equal(
  mappedSourcePrimaryKeys([...fields, { source: "id", target: "second_id" }], source),
  undefined,
);
assert.equal(
  mappedSourcePrimaryKeys(
    fields,
    source.map((column) => ({ ...column, nullable: true })),
  ),
  undefined,
);
assert.equal(
  mappedSourcePrimaryKeys(
    fields,
    source.map(({ nullable: _nullable, ...column }) => column),
  ),
  undefined,
);
assert.equal(
  mappedSourcePrimaryKeys(
    fields.map((field) => ({ ...field, target: "duplicate" })),
    source,
  ),
  undefined,
);
assert.equal(
  mappedSourcePrimaryKeys(
    fields.map((field) => ({ ...field, target: "a,b" })),
    source,
  ),
  undefined,
);

const original = Object.freeze([
  Object.freeze({
    "source-table": selector,
    projection: " id, CONCAT(name, ', ', CAST(amount AS STRING)) AS label ",
    filter: "label <> 'x,y'",
    "primary-keys": " id ",
    description: "Keep the original expression spelling",
  }),
  Object.freeze({
    "source-table": "app\\..*",
    projection: "*",
    "table-options": "engine=InnoDB",
    "table-options.delimiter": ";",
  }),
]);
assert.equal(canEditFieldMapping(original, selector), false);
assert.equal(canEditFieldMapping([original[0]], selector), true);
assert.equal(canEditFieldMapping([{ "source-table": "app\\..*" }], selector), false);
assert.equal(
  canEditFieldMapping([{ "source-table": selector, "partition-keys": "id" }], selector),
  false,
);
assert.equal(canEditFieldMapping([], selector), true);

const filtered = updateTransformRule(original, 0, selector, { filter: "amount > 10" });
assert.equal(filtered[0].projection, original[0].projection);
assert.equal(filtered[0]["primary-keys"], " id ");
assert.equal(filtered[0].description, original[0].description);
assert.equal(filtered[1], original[1]);
assert.equal(original[0].filter, "label <> 'x,y'");
const cleared = updateTransformRule(filtered, 0, selector, { filter: undefined });
assert.equal("filter" in cleared[0], false);
assert.equal(cleared[0].projection, original[0].projection);

const interleaved = [
  original[1],
  original[0],
  { "source-table": "other.*" },
  { ...original[0], filter: "id > 0" },
  original[1],
];
const replacementRules = [
  { "source-table": selector, projection: "`id`" },
  { "source-table": selector, projection: "`name`" },
  { "source-table": selector, filter: "id > 3" },
];
assert.deepEqual(replaceTableTransforms(interleaved, selector, replacementRules), [
  interleaved[0],
  replacementRules[0],
  interleaved[2],
  replacementRules[1],
  replacementRules[2],
  interleaved[4],
]);
assert.deepEqual(replaceTableTransforms(interleaved, selector, []), [
  interleaved[0],
  interleaved[2],
  interleaved[4],
]);
assert.deepEqual(replaceTableTransforms([original[1]], selector, replacementRules), [
  original[1],
  ...replacementRules,
]);
assert.throws(() => replaceTableTransforms(interleaved, selector, [{ "source-table": "other.*" }]));

const created = updateTransformRule([], 0, selector, { projection });
assert.deepEqual(created, [{ "source-table": selector, projection }]);
const keyed = updateTransformRule(created, 0, selector, {
  "primary-keys": mappedSourcePrimaryKeys(fields, source).join(", "),
});
assert.deepEqual(JSON.parse(JSON.stringify(keyed)), keyed);
assert.deepEqual(readDirectProjection(keyed[0].projection).fields, fields);
assert.equal(transformEditingError(keyed), undefined);
assert.match(transformEditingError([{ "source-table": " " }]), /来源表/);
assert.match(
  transformEditingError([
    { "source-table": selector, projection: fieldProjection([{ source: "id", target: "" }]) },
  ]),
  /不能为空/,
);
assert.match(
  transformEditingError([
    {
      "source-table": selector,
      projection: fieldProjection([
        { source: "id", target: "same" },
        { source: "tenant", target: "SAME" },
      ]),
    },
  ]),
  /不能重复/,
);
assert.equal(transformEditingError([{ "source-table": selector, projection: "CAST(" }]), undefined);

console.log(
  "Direct field mapping, quoted aliases, physical keys, and lossless expression editing contracts passed.",
);
