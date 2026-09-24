import { YAK_OPS_MENU_CODES } from '../constants/securityMenuCodes';
import {
  appRoutes,
  canAccessNavigationRoute,
  getActiveNavigationId,
  getMainNavigationGroups,
  getQuickCreateRoutes,
  getStandaloneNavigationRoutes,
  resolveNavigationMenuCode,
} from './navigation';

describe('permission-aware navigation', () => {
  const batchRead = ['task:batch:read'];
  const developmentRead = ['data-development:read'];
  const dataServiceRead = ['data-service:read'];
  const dataServiceAll = [
    'data-service:read',
    'data-service:runtime',
    'data-service:observe',
  ];

  it('uses route permission metadata and lets details inherit their parent', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;
    expect(canAccessNavigationRoute(list, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(list, [])).toBe(false);
    expect(canAccessNavigationRoute(detail, batchRead)).toBe(true);
    expect(canAccessNavigationRoute(detail, [])).toBe(false);
    expect(getActiveNavigationId('/sync/batch-link-up/42/detail', batchRead)).toBe('batch-link-up');
  });

  it('uses stable menu codes for protected routes and hidden descendants', () => {
    const list = appRoutes.find((route) => route.id === 'batch-link-up')!;
    const detail = appRoutes.find((route) => route.id === 'batch-link-up-detail')!;

    expect(list.menuCode).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(resolveNavigationMenuCode(detail)).toBe(YAK_OPS_MENU_CODES.batchLinkUp);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(
      canAccessNavigationRoute(list, batchRead, [YAK_OPS_MENU_CODES.dataSource]),
    ).toBe(false);
    expect(
      canAccessNavigationRoute(detail, batchRead, [YAK_OPS_MENU_CODES.batchLinkUp]),
    ).toBe(true);
    expect(getActiveNavigationId(
      '/sync/batch-link-up/42/detail',
      batchRead,
      [YAK_OPS_MENU_CODES.dataSource],
    )).toBeUndefined();
  });

  it('hides non-datasource navigation in datasource focus mode', () => {
    expect(appRoutes.filter((route) => !route.hidden).map((route) => route.id)).toEqual([
      'data-source',
    ]);
    expect(getMainNavigationGroups(['security:root'])).toEqual([]);
    expect(getQuickCreateRoutes([...batchRead, 'task:batch:create'])).toEqual([]);
  });

  it('does not re-expose focused-out routes through menu grants', () => {
    expect(
      getMainNavigationGroups(
        batchRead,
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ),
    ).toEqual([]);
    expect(
      getQuickCreateRoutes(
        [...batchRead, 'task:batch:create'],
        [YAK_OPS_MENU_CODES.batchLinkUp],
      ),
    ).toEqual([]);
  });

  it('keeps grouped sidebar navigation empty in datasource focus mode', () => {
    expect(getMainNavigationGroups(['security:root'])).toEqual([]);
  });

  it('hides data-consumption entries while preserving their route metadata', () => {
    expect(getMainNavigationGroups([]).find(
      (group) => group.id === 'data-analysis',
    )).toBeUndefined();
    expect(getActiveNavigationId('/dashboard', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/new', [])).toBe('dashboard');
    expect(getActiveNavigationId('/dashboard/42', [])).toBe('dashboard');
    expect(getActiveNavigationId('/data-analysis/chart-analysis', [])).toBe('dashboard');
  });

  it('keeps hidden data-service routes permission-aware and addressable', () => {
    expect(getMainNavigationGroups(dataServiceAll).find(
      (group) => group.id === 'data-service',
    )).toBeUndefined();
    expect(getActiveNavigationId('/data-service/api/42', dataServiceRead)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-service/debug', ['data-service:runtime'])).toBeUndefined();
    expect(getActiveNavigationId('/data-service/overview', dataServiceRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-service', dataServiceAll)).toBe('data-service-api');
    expect(getActiveNavigationId('/data-service/debug', dataServiceAll)).toBe('data-service-debug');
    expect(getActiveNavigationId('/data-service/overview', dataServiceAll)).toBe('data-service-overview');
    expect(getActiveNavigationId('/data-service/logs', dataServiceAll)).toBe('data-service-logs');
  });

  it('shows datasource as the only standalone navigation entry', () => {
    expect(getStandaloneNavigationRoutes(['security:root']).map((route) => route.id)).toEqual([
      'data-source',
    ]);
    expect(getActiveNavigationId('/home', [])).toBe('home');
  });

  it('keeps datasource navigation protected by its menu grant', () => {
    expect(
      getStandaloneNavigationRoutes(
        ['resource:data-source:read'],
        [],
      ).map((route) => route.id),
    ).toEqual([]);
    expect(
      getStandaloneNavigationRoutes(
        ['resource:data-source:read'],
        [YAK_OPS_MENU_CODES.dataSource],
      ).map((route) => route.id),
    ).toEqual(['data-source']);
  });

  it('keeps the personal settings page addressable without exposing it in the main sidebar', () => {
    expect(getStandaloneNavigationRoutes([]).map((route) => route.id)).not.toContain('settings');
    expect(getActiveNavigationId('/settings', [])).toBe('settings');
  });

  it('hides data-development navigation while keeping route permission semantics', () => {
    expect(getMainNavigationGroups(developmentRead).find(
      (group) => group.id === 'development',
    )).toBeUndefined();
    expect(getActiveNavigationId('/data-development', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', [])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/task/42', developmentRead)).toBe(
      'data-development',
    );
    expect(getActiveNavigationId('/data-development/releases', developmentRead)).toBe(
      'data-development-release',
    );
    expect(getActiveNavigationId('/data-development/executions', developmentRead)).toBe(
      'data-development-execution',
    );
  });

  it('hides data-quality navigation while keeping hidden routes addressable', () => {
    const qualityPermissions = [
      'quality:monitor:read',
      'quality:execution:read',
      'quality:template:read',
    ];
    expect(getMainNavigationGroups(qualityPermissions).find(
      (group) => group.id === 'data-quality',
    )).toBeUndefined();
    expect(getActiveNavigationId('/data-quality/monitor/create', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/monitor/42', qualityPermissions)).toBe(
      'data-quality-table-config',
    );
    expect(getActiveNavigationId('/data-quality/execution', qualityPermissions)).toBe(
      'data-quality-execution',
    );
    expect(
      getActiveNavigationId('/data-quality/execution/QM-20260807095619-ABC123', qualityPermissions),
    ).toBe('data-quality-execution');
  });

  it('does not expose removed modules', () => {
    expect(getActiveNavigationId('/sync/realtime-link-up', ['task:realtime:read'])).toBeUndefined();
    expect(getActiveNavigationId('/data-development/workbench', developmentRead)).toBeUndefined();
    expect(getActiveNavigationId('/data-quality/report', ['quality:report:read'])).toBeUndefined();
  });
});