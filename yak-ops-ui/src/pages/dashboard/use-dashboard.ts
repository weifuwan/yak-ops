import { fetchAnalyses } from '@/components/analysis/analysis-service';
import type {
  AnalysisFilter,
  AnalysisSelection,
  Scalar,
} from '@/components/analysis/model';
import { getIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_DASHBOARD } from './defaults';
import {
  createDashboard,
  fetchDashboard,
  fetchPublishedDashboard,
  publishDashboard,
  restoreDashboardVersion,
  saveDashboardVersion,
  toDashboardDocument,
} from './dashboard-service';
import {
  cloneDashboard,
  createInlineAnalysis,
  createWidget,
  findDataset,
  isPersistedDashboard,
  reconcileDashboard,
  STORAGE_KEY,
} from './helpers';
import type {
  AnalysisAsset,
  ChartType,
  DashboardDocument,
  DashboardDrillStep,
  DashboardGlobalFilter,
  DashboardInlineAnalysisSpec,
  DashboardInteraction,
  DashboardServerDetail,
  DashboardTheme,
  DashboardVersionDetail,
  DashboardVersionSummary,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { fetchDashboardDatasets } from './service';

const HISTORY_LIMIT = 50;
const HISTORY_MERGE_WINDOW = 500;

const createDefaultDashboard = (): DashboardDocument => ({
  ...cloneDashboard(DEFAULT_DASHBOARD),
  name: getIntl().formatMessage({ id: 'pages.dashboard.editor.defaultName' }),
});

const assetSpec = (analysis: AnalysisAsset): DashboardInlineAnalysisSpec => ({
  type: analysis.type,
  datasetId: analysis.datasetId,
  dimensions: [...analysis.dimensions],
  metrics: analysis.metrics.map((metric) => ({ ...metric })),
  filters: analysis.filters.map((filter, index) => ({
    ...filter,
    id: `detached-${analysis.id}-${index}`,
  })),
  sort: analysis.sort ? { ...analysis.sort } : undefined,
  style: { ...analysis.style },
  limit: analysis.limit,
  timeoutSeconds: analysis.timeoutSeconds,
});

const runtimeDefaults = (filters: DashboardGlobalFilter[]) => Object.fromEntries(
  filters.map((filter) => [filter.id, filter.defaultValue]),
) as Record<string, Scalar | undefined>;

const hasOwn = (value: Record<string, Scalar | undefined>, key: string) =>
  Object.prototype.hasOwnProperty.call(value, key);

const dashboardFingerprint = (dashboard: DashboardDocument) => JSON.stringify({
  name: dashboard.name,
  description: dashboard.description,
  activeDatasetId: dashboard.activeDatasetId,
  theme: dashboard.theme,
  widgets: dashboard.widgets,
  globalFilters: dashboard.globalFilters,
  interactions: dashboard.interactions,
});

const trimHistory = (items: DashboardDocument[]) => (
  items.length > HISTORY_LIMIT ? items.slice(items.length - HISTORY_LIMIT) : items
);

const publishedDocument = (detail: DashboardVersionDetail): DashboardDocument => ({
  version: 1,
  id: detail.dashboard.id,
  name: detail.version.name,
  description: detail.version.description,
  activeDatasetId: detail.version.activeDatasetId || '',
  theme: detail.theme,
  widgets: detail.widgets,
  globalFilters: detail.globalFilters,
  interactions: detail.interactions,
  currentVersionNo: detail.version.versionNo,
  currentVersionId: detail.version.id,
  publishedVersionNo: detail.dashboard.publishedVersionNo,
  publishedVersionId: detail.dashboard.publishedVersionId,
  publishedAt: detail.dashboard.publishedTime,
  updatedAt: detail.dashboard.updateTime,
});

export function useDashboardDesigner(
  dashboardId?: string,
  initialPreview = false,
  publishedView = false,
) {
  const initialDashboard = useMemo(createDefaultDashboard, []);
  const [dashboard, setDashboardState] = useState<DashboardDocument>(initialDashboard);
  const dashboardRef = useRef<DashboardDocument>(initialDashboard);
  const savedFingerprintRef = useRef(dashboardFingerprint(initialDashboard));
  const undoStackRef = useRef<DashboardDocument[]>([]);
  const redoStackRef = useRef<DashboardDocument[]>([]);
  const lastHistoryRef = useRef<{ key: string; at: number }>();

  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [analyses, setAnalyses] = useState<AnalysisAsset[]>([]);
  const [dashboardVersions, setDashboardVersions] = useState<DashboardVersionSummary[]>([]);
  const [dashboardSaving, setDashboardSaving] = useState(false);
  const [dashboardPublishing, setDashboardPublishing] = useState(false);
  const [runtimeFilterValues, setRuntimeFilterValues] = useState<Record<string, Scalar | undefined>>({});
  const [drillPaths, setDrillPaths] = useState<Record<string, DashboardDrillStep[]>>({});
  const [selectedId, setSelectedId] = useState<string>();
  const [preview, setPreview] = useState(initialPreview || publishedView);

  const widgets = dashboard.widgets;
  const selectedWidget = widgets.find((widget) => widget.id === selectedId);
  const activeDataset = useMemo(
    () => findDataset(datasets, dashboard.activeDatasetId),
    [dashboard.activeDatasetId, datasets],
  );
  const dirty = dashboardFingerprint(dashboard) !== savedFingerprintRef.current;
  const canUndo = undoStackRef.current.length > 0;
  const canRedo = redoStackRef.current.length > 0;
  const persisted = isPersistedDashboard(dashboard.id);
  const hasPublishedVersion = Boolean(dashboard.publishedVersionId);
  const hasUnpublishedDraft = Boolean(
    dashboard.currentVersionId
    && dashboard.currentVersionId !== dashboard.publishedVersionId,
  );
  const canPublish = !persisted || dirty || hasUnpublishedDraft || !hasPublishedVersion;

  const setDashboardWithoutHistory = useCallback((next: DashboardDocument) => {
    const cloned = cloneDashboard(next);
    dashboardRef.current = cloned;
    setDashboardState(cloned);
    setSelectedId((current) => (
      current && cloned.widgets.some((widget) => widget.id === current) ? current : undefined
    ));
  }, []);

  const resetDashboardState = useCallback((next: DashboardDocument, markSaved = true) => {
    const cloned = cloneDashboard(next);
    dashboardRef.current = cloned;
    undoStackRef.current = [];
    redoStackRef.current = [];
    lastHistoryRef.current = undefined;
    if (markSaved) savedFingerprintRef.current = dashboardFingerprint(cloned);
    setDashboardState(cloned);
    setSelectedId(undefined);
    setDrillPaths({});
  }, []);

  const applyServerDetail = useCallback((detail: DashboardServerDetail) => {
    const document = toDashboardDocument(detail);
    resetDashboardState(document);
    setDashboardVersions(detail.versions);
    window.localStorage.removeItem(STORAGE_KEY);
    return document;
  }, [resetDashboardState]);

  const commitDashboard = useCallback((
    updater: DashboardDocument | ((current: DashboardDocument) => DashboardDocument),
    historyKey: string,
  ) => {
    const current = dashboardRef.current;
    const next = typeof updater === 'function' ? updater(current) : updater;
    if (dashboardFingerprint(current) === dashboardFingerprint(next)) return;

    const now = Date.now();
    const last = lastHistoryRef.current;
    const mergeWithPrevious = Boolean(
      last
      && last.key === historyKey
      && now - last.at <= HISTORY_MERGE_WINDOW,
    );

    if (!mergeWithPrevious) {
      undoStackRef.current = trimHistory([
        ...undoStackRef.current,
        cloneDashboard(current),
      ]);
    }
    redoStackRef.current = [];
    lastHistoryRef.current = { key: historyKey, at: now };
    setDashboardWithoutHistory(next);
  }, [setDashboardWithoutHistory]);

  const undo = useCallback(() => {
    const previous = undoStackRef.current.at(-1);
    if (!previous) return;
    undoStackRef.current = undoStackRef.current.slice(0, -1);
    redoStackRef.current = trimHistory([
      ...redoStackRef.current,
      cloneDashboard(dashboardRef.current),
    ]);
    lastHistoryRef.current = undefined;
    setDashboardWithoutHistory(previous);
  }, [setDashboardWithoutHistory]);

  const redo = useCallback(() => {
    const next = redoStackRef.current.at(-1);
    if (!next) return;
    redoStackRef.current = redoStackRef.current.slice(0, -1);
    undoStackRef.current = trimHistory([
      ...undoStackRef.current,
      cloneDashboard(dashboardRef.current),
    ]);
    lastHistoryRef.current = undefined;
    setDashboardWithoutHistory(next);
  }, [setDashboardWithoutHistory]);

  const loadDatasets = useCallback(async () => {
    setDatasetsLoading(true);
    try {
      setDatasets(await fetchDashboardDatasets());
    } catch {
      setDatasets([]);
    } finally {
      setDatasetsLoading(false);
    }
  }, []);

  const loadAnalyses = useCallback(async () => {
    try {
      setAnalyses(await fetchAnalyses());
    } catch {
      setAnalyses([]);
    }
  }, []);

  const openDashboard = useCallback(async (targetDashboardId: string) => {
    try {
      if (publishedView) {
        const detail = await fetchPublishedDashboard(targetDashboardId);
        resetDashboardState(publishedDocument(detail));
        setDashboardVersions([]);
        window.localStorage.removeItem(STORAGE_KEY);
        return;
      }
      applyServerDetail(await fetchDashboard(targetDashboardId));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.loadFailed' }),
      );
    }
  }, [applyServerDetail, publishedView, resetDashboardState]);

  useEffect(() => {
    void loadDatasets();
    void loadAnalyses();
  }, [loadDatasets, loadAnalyses]);

  useEffect(() => {
    setPreview(initialPreview || publishedView);
    if (dashboardId) {
      void openDashboard(dashboardId);
      return;
    }
    resetDashboardState(createDefaultDashboard());
    setDashboardVersions([]);
    setRuntimeFilterValues({});
  }, [dashboardId, initialPreview, openDashboard, publishedView, resetDashboardState]);

  useEffect(() => {
    if (!datasets.length) return;
    const current = dashboardRef.current;
    const next = reconcileDashboard(current, datasets);
    if (dashboardFingerprint(current) === dashboardFingerprint(next)) return;

    const wasClean = dashboardFingerprint(current) === savedFingerprintRef.current;
    if (wasClean) savedFingerprintRef.current = dashboardFingerprint(next);
    setDashboardWithoutHistory(next);
  }, [datasets, setDashboardWithoutHistory]);

  useEffect(() => {
    setRuntimeFilterValues(runtimeDefaults(dashboard.globalFilters));
    setDrillPaths({});
  }, [dashboard.id, dashboard.currentVersionId]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const field = params.get('df');
    const value = params.get('dv');
    if (!field || value === null) return;
    const targetIds = dashboard.globalFilters
      .filter((filter) => filter.bindings.some((binding) => binding.field === field))
      .map((filter) => filter.id);
    if (!targetIds.length) return;
    setRuntimeFilterValues((current) => {
      const next = { ...current };
      targetIds.forEach((filterId) => {
        next[filterId] = value;
      });
      return next;
    });
  }, [dashboard.id, dashboard.currentVersionId, dashboard.globalFilters]);

  useEffect(() => {
    if (!dirty || publishedView) return undefined;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [dirty, publishedView]);

  const updateDashboardName = (name: string) => commitDashboard(
    (current) => ({ ...current, name }),
    'dashboard:name',
  );

  const updateDashboardTheme = (theme: DashboardTheme) => commitDashboard(
    (current) => ({ ...current, theme }),
    'dashboard:theme',
  );

  const updateLayout = (
    nextLayout: readonly { i: string; x: number; y: number; w: number; h: number }[],
  ) => {
    const nextMap = new Map(nextLayout.map((item) => [item.i, item]));
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        const next = nextMap.get(widget.id);
        return next ? { ...widget, x: next.x, y: next.y, w: next.w, h: next.h } : widget;
      }),
    }), 'dashboard:layout');
  };

  const updateWidget = (id: string, patch: Partial<DashboardWidget>) => commitDashboard(
    (current) => ({
      ...current,
      widgets: current.widgets.map((widget) => widget.id === id ? { ...widget, ...patch } : widget),
    }),
    `widget:${id}:${Object.keys(patch).sort().join(',')}`,
  );

  const updateInlineAnalysis = (id: string, patch: Partial<DashboardInlineAnalysisSpec>) => commitDashboard(
    (current) => ({
      ...current,
      widgets: current.widgets.map((widget) => {
        if (widget.id !== id || widget.analysisId || !widget.inlineAnalysis) return widget;
        return { ...widget, inlineAnalysis: { ...widget.inlineAnalysis, ...patch } };
      }),
    }),
    `widget:${id}:analysis:${Object.keys(patch).sort().join(',')}`,
  );

  const updateGlobalFilters = (filters: DashboardGlobalFilter[]) => {
    const filterIds = new Set(filters.map((filter) => filter.id));
    commitDashboard((current) => ({
      ...current,
      globalFilters: filters,
      interactions: current.interactions.filter((interaction) => filterIds.has(interaction.targetFilterId)),
    }), 'dashboard:global-filters');
    setRuntimeFilterValues((current) => Object.fromEntries(
      filters.map((filter) => [
        filter.id,
        hasOwn(current, filter.id) ? current[filter.id] : filter.defaultValue,
      ]),
    ));
  };

  const updateInteractions = (interactions: DashboardInteraction[]) => commitDashboard(
    (current) => ({ ...current, interactions }),
    'dashboard:interactions',
  );

  const setRuntimeFilterValue = (filterId: string, value: Scalar | undefined) => {
    setRuntimeFilterValues((current) => ({ ...current, [filterId]: value }));
  };

  const resetRuntimeFilters = () => setRuntimeFilterValues(runtimeDefaults(dashboard.globalFilters));

  const runtimeSpecForWidget = useCallback((widgetId: string): DashboardInlineAnalysisSpec | AnalysisAsset | undefined => {
    const widget = dashboard.widgets.find((item) => item.id === widgetId);
    if (!widget) return undefined;
    const base = widget.analysisId
      ? analyses.find((analysis) => analysis.id === widget.analysisId)
      : widget.inlineAnalysis;
    if (!base || widget.analysisId) return base;
    const behavior = widget.inlineAnalysis?.dashboardBehavior;
    const hierarchy = behavior?.clickAction === 'drill' ? behavior.drillFields || [] : [];
    if (hierarchy.length < 2 || hierarchy[0] !== base.dimensions[0]) return base;
    const path = drillPaths[widgetId] || [];
    const currentField = hierarchy[Math.min(path.length, hierarchy.length - 1)];
    if (!currentField) return base;
    const otherDimensions = base.dimensions.filter((field) => !hierarchy.includes(field));
    return {
      ...base,
      dimensions: [currentField, ...otherDimensions],
    };
  }, [analyses, dashboard.widgets, drillPaths]);

  const drillPathForWidget = useCallback(
    (widgetId: string) => drillPaths[widgetId] || [],
    [drillPaths],
  );

  const drillBack = useCallback((widgetId: string, depth: number) => {
    setDrillPaths((current) => {
      const path = current[widgetId] || [];
      const nextPath = path.slice(0, Math.max(0, depth));
      if (nextPath.length === path.length) return current;
      return { ...current, [widgetId]: nextPath };
    });
  }, []);

  const runtimeFiltersForWidget = useCallback((widgetId: string): AnalysisFilter[] => {
    const globalFilters = dashboard.globalFilters.flatMap((filter) => {
      const binding = filter.bindings.find((item) => item.widgetId === widgetId);
      if (!binding) return [];
      const value = hasOwn(runtimeFilterValues, filter.id)
        ? runtimeFilterValues[filter.id]
        : filter.defaultValue;
      if (value === undefined || value === null || value === '') return [];
      return [{
        id: `dashboard-${filter.id}`,
        field: binding.field,
        operator: filter.operator,
        value: String(value),
      } satisfies AnalysisFilter];
    });
    const drillFilters = (drillPaths[widgetId] || []).flatMap((step, index) => {
      if (step.value === undefined || step.value === null || step.value === '') return [];
      return [{
        id: `dashboard-drill-${widgetId}-${index}`,
        field: step.field,
        operator: 'eq',
        value: String(step.value),
      } satisfies AnalysisFilter];
    });
    return [...globalFilters, ...drillFilters];
  }, [dashboard.globalFilters, drillPaths, runtimeFilterValues]);

  const handleWidgetSelection = useCallback((widgetId: string, selection: AnalysisSelection) => {
    const matched = dashboard.interactions.filter((interaction) => (
      interaction.event === 'select'
      && interaction.sourceWidgetId === widgetId
      && interaction.sourceField === selection.fieldId
    ));
    if (matched.length) {
      setRuntimeFilterValues((current) => {
        const next = { ...current };
        matched.forEach((interaction) => {
          next[interaction.targetFilterId] = selection.value;
        });
        return next;
      });
    }

    const widget = dashboard.widgets.find((item) => item.id === widgetId);
    const behavior = widget?.inlineAnalysis?.dashboardBehavior;
    if (!behavior || !behavior.clickAction || behavior.clickAction === 'none') return undefined;

    if (behavior.clickAction === 'drill') {
      const hierarchy = behavior.drillFields || [];
      if (hierarchy.length < 2 || hierarchy[0] !== widget.inlineAnalysis?.dimensions[0]) return undefined;
      setDrillPaths((current) => {
        const path = current[widgetId] || [];
        const currentField = hierarchy[path.length];
        if (selection.fieldId !== currentField || path.length >= hierarchy.length - 1) return current;
        return {
          ...current,
          [widgetId]: [
            ...path,
            {
              field: selection.fieldId,
              value: selection.value,
              label: String(selection.value),
            },
          ],
        };
      });
      return undefined;
    }

    if (behavior.clickAction === 'dashboard' && behavior.targetDashboardId) {
      const params = new URLSearchParams({
        preview: '1',
        published: '1',
        df: selection.fieldId,
        dv: String(selection.value),
      });
      return `/dashboard/${behavior.targetDashboardId}?${params.toString()}`;
    }

    if (behavior.clickAction === 'yak' && behavior.targetPath) {
      const params = new URLSearchParams();
      params.set(behavior.queryParam?.trim() || selection.fieldId, String(selection.value));
      return `${behavior.targetPath}?${params.toString()}`;
    }

    return undefined;
  }, [dashboard.interactions, dashboard.widgets]);

  const maxY = () => dashboardRef.current.widgets.reduce(
    (value, widget) => Math.max(value, widget.y + widget.h),
    0,
  );

  const addWidget = (type: ChartType) => {
    if (!activeDataset) {
      message.info(getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.datasetRequired' }));
      return;
    }
    const next = createWidget(type, activeDataset, maxY());
    commitDashboard(
      (current) => ({ ...current, widgets: [...current.widgets, next] }),
      `widget:add:${next.id}`,
    );
    setSelectedId(next.id);
  };

  const detachAnalysis = (id: string) => {
    const widget = dashboardRef.current.widgets.find((item) => item.id === id);
    if (!widget?.analysisId) return;
    const analysis = analyses.find((item) => item.id === widget.analysisId);
    if (!analysis) {
      message.warning(getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.analysisUnavailable' }));
      return;
    }
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.map((item) => item.id === id ? {
        ...item,
        analysisId: undefined,
        title: analysis.name,
        inlineAnalysis: assetSpec(analysis),
      } : item),
    }), `widget:${id}:detach`);
    message.success(getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.analysisDetached' }));
  };

  const duplicateWidget = (id: string) => {
    const source = dashboardRef.current.widgets.find((widget) => widget.id === id);
    if (!source) return;
    const next: DashboardWidget = {
      ...source,
      id: `widget-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      y: maxY(),
      inlineAnalysis: source.inlineAnalysis
        ? JSON.parse(JSON.stringify(source.inlineAnalysis)) as DashboardInlineAnalysisSpec
        : undefined,
    };
    commitDashboard(
      (current) => ({ ...current, widgets: [...current.widgets, next] }),
      `widget:duplicate:${id}`,
    );
    setSelectedId(next.id);
  };

  const deleteWidget = (id: string) => {
    commitDashboard((current) => ({
      ...current,
      widgets: current.widgets.filter((widget) => widget.id !== id),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }), `widget:delete:${id}`);
    setSelectedId((current) => current === id ? undefined : current);
    setDrillPaths((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
  };

  const changeWidgetDataset = (id: string, datasetId: string) => {
    const widget = dashboardRef.current.widgets.find((item) => item.id === id);
    if (widget?.analysisId) {
      message.info(getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.sharedChartReadonly' }));
      return;
    }
    if (!widget?.inlineAnalysis) return;
    const dataset = findDataset(datasets, datasetId);
    if (!dataset) return;
    commitDashboard((current) => ({
      ...current,
      activeDatasetId: datasetId,
      widgets: current.widgets.map((item) => item.id === id
        ? { ...item, inlineAnalysis: createInlineAnalysis(item.inlineAnalysis!.type, dataset) }
        : item),
      globalFilters: current.globalFilters.map((filter) => ({
        ...filter,
        bindings: filter.bindings.filter((binding) => binding.widgetId !== id),
      })),
      interactions: current.interactions.filter((interaction) => interaction.sourceWidgetId !== id),
    }), `widget:${id}:dataset`);
    setDrillPaths((current) => ({ ...current, [id]: [] }));
  };

  const persistCurrentDraft = async (): Promise<DashboardServerDetail | undefined> => {
    const currentDashboard = dashboardRef.current;
    if (!currentDashboard.name.trim()) {
      message.warning(getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.nameRequired' }));
      return undefined;
    }
    return isPersistedDashboard(currentDashboard.id)
      ? saveDashboardVersion(currentDashboard.id, currentDashboard)
      : createDashboard(currentDashboard);
  };

  const saveDraft = async (): Promise<string | undefined> => {
    const currentDashboard = dashboardRef.current;
    if (isPersistedDashboard(currentDashboard.id) && !dirty) return currentDashboard.id;
    setDashboardSaving(true);
    try {
      const detail = await persistCurrentDraft();
      if (!detail) return undefined;
      applyServerDetail(detail);
      message.success(getIntl().formatMessage(
        { id: 'pages.dashboard.editor.designer.draftSaved' },
        { version: detail.dashboard.currentVersionNo },
      ));
      return detail.dashboard.id;
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.saveFailed' }),
      );
      return undefined;
    } finally {
      setDashboardSaving(false);
    }
  };

  const publish = async (): Promise<string | undefined> => {
    if (!canPublish || dashboardPublishing) return dashboardRef.current.id;
    setDashboardPublishing(true);
    try {
      let dashboardIdToPublish = dashboardRef.current.id;
      if (!isPersistedDashboard(dashboardIdToPublish) || dirty) {
        const draftDetail = await persistCurrentDraft();
        if (!draftDetail) return undefined;
        applyServerDetail(draftDetail);
        dashboardIdToPublish = draftDetail.dashboard.id;
      }
      const detail = await publishDashboard(dashboardIdToPublish);
      applyServerDetail(detail);
      message.success(getIntl().formatMessage(
        { id: 'pages.dashboard.editor.designer.published' },
        { version: detail.dashboard.publishedVersionNo },
      ));
      return detail.dashboard.id;
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.publishFailed' }),
      );
      return undefined;
    } finally {
      setDashboardPublishing(false);
    }
  };

  const restoreVersion = async (versionNo: number) => {
    const currentDashboard = dashboardRef.current;
    if (!isPersistedDashboard(currentDashboard.id)) return;
    setDashboardSaving(true);
    try {
      const detail = await restoreDashboardVersion(currentDashboard.id, versionNo);
      applyServerDetail(detail);
      message.success(getIntl().formatMessage(
        { id: 'pages.dashboard.editor.designer.restored' },
        { version: versionNo, draftVersion: detail.dashboard.currentVersionNo },
      ));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : getIntl().formatMessage({ id: 'pages.dashboard.editor.designer.restoreFailed' }),
      );
    } finally {
      setDashboardSaving(false);
    }
  };

  return {
    dashboard,
    widgets,
    datasets,
    datasetsLoading,
    analyses,
    dashboardVersions,
    dashboardSaving,
    dashboardPublishing,
    runtimeFilterValues,
    drillPaths,
    selectedWidget,
    activeDataset,
    selectedId,
    preview,
    dirty,
    canUndo,
    canRedo,
    persisted,
    publishedView,
    hasPublishedVersion,
    hasUnpublishedDraft,
    canPublish,
    setSelectedId,
    setPreview,
    updateDashboardName,
    updateDashboardTheme,
    updateLayout,
    updateWidget,
    updateInlineAnalysis,
    updateGlobalFilters,
    updateInteractions,
    setRuntimeFilterValue,
    resetRuntimeFilters,
    runtimeSpecForWidget,
    runtimeFiltersForWidget,
    drillPathForWidget,
    drillBack,
    handleWidgetSelection,
    addWidget,
    detachAnalysis,
    duplicateWidget,
    deleteWidget,
    changeWidgetDataset,
    undo,
    redo,
    saveDraft,
    publish,
    restoreVersion,
  };
}
