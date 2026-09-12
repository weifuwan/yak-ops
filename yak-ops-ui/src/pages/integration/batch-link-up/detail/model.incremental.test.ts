import {
  buildSavePayload,
  normalizeEditDetail,
  type SyncEditorState,
} from './model';

const detail = (incremental?: Record<string, unknown>) => ({
  id: '10',
  basic: { jobName: 'orders', jobDesc: '', mode: 'GUIDE_SINGLE' },
  source: {
    connectorId: 'jdbc',
    dbType: 'MYSQL',
    dataSourceId: '1',
    config: { readMode: 'table', table: 'orders' },
  },
  sink: {
    connectorId: 'jdbc',
    dbType: 'MYSQL',
    dataSourceId: '2',
    config: { table: 'orders_copy', writeMode: 'upsert', primaryKey: 'id' },
  },
  incremental,
});

test('old task detail defaults to disabled incremental mode', () => {
  const editor = normalizeEditDetail(detail(), '10');
  expect(editor.incremental).toEqual({
    enabled: false,
    strategy: 'MAX_TIMESTAMP',
    column: '',
    bootstrapMode: 'SOURCE_CURRENT_MAX',
  });
});

test('incremental settings round-trip through editor payload', () => {
  const editor = normalizeEditDetail(
    detail({
      enabled: true,
      strategy: 'MAX_TIMESTAMP',
      column: 'UPDATED_AT',
      bootstrapMode: 'SOURCE_CURRENT_MAX',
    }),
    '10',
  ) as SyncEditorState;

  expect(buildSavePayload(editor).incremental).toEqual({
    enabled: true,
    strategy: 'MAX_TIMESTAMP',
    column: 'UPDATED_AT',
    bootstrapMode: 'SOURCE_CURRENT_MAX',
  });
});
