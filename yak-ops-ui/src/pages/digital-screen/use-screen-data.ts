import type { ScreenDataOverrides, ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/components/analysis/model';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { DigitalScreenBindings } from './model';
import {
  canQueryScreenComponent,
  queryScreenComponentData,
} from './screen-data-service';

interface ScreenRuntimeDataState {
  data: ScreenDataOverrides;
  loadingIds: string[];
  errors: Record<string, string>;
}

const EMPTY_STATE: ScreenRuntimeDataState = {
  data: {},
  loadingIds: [],
  errors: {},
};

export const useScreenRuntimeData = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
  datasets: PublishedDataset[],
) => {
  const [state, setState] = useState<ScreenRuntimeDataState>(EMPTY_STATE);
  const sequence = useRef(0);
  const bindingKey = useMemo(() => JSON.stringify(bindings), [bindings]);
  const datasetKey = useMemo(
    () => datasets.map((dataset) => `${dataset.id}:${dataset.currentVersionNo ?? ''}`).join('|'),
    [datasets],
  );

  useEffect(() => {
    if (!template) {
      setState(EMPTY_STATE);
      return undefined;
    }

    const candidates = template.components.flatMap((component) => {
      const binding = bindings[component.id];
      if (!binding || !canQueryScreenComponent(component, binding)) return [];
      const dataset = datasets.find((item) => item.id === binding.datasetId);
      if (!dataset) return [];
      return [{ component, binding, dataset }];
    });

    if (!candidates.length) {
      setState(EMPTY_STATE);
      return undefined;
    }

    const requestId = ++sequence.current;
    const loadingIds = candidates.map(({ component }) => component.id);
    setState({ data: {}, loadingIds, errors: {} });

    const timer = window.setTimeout(async () => {
      const results = await Promise.allSettled(candidates.map(async ({ component, binding, dataset }) => ({
        componentId: component.id,
        data: await queryScreenComponentData(component, binding, dataset),
      })));
      if (requestId !== sequence.current) return;

      const data: ScreenDataOverrides = {};
      const errors: Record<string, string> = {};
      results.forEach((result, index) => {
        const componentId = candidates[index].component.id;
        if (result.status === 'fulfilled') {
          if (result.value.data) data[result.value.componentId] = result.value.data;
          return;
        }
        errors[componentId] = result.reason instanceof Error
          ? result.reason.message
          : 'Dataset 查询失败';
      });
      setState({ data, loadingIds: [], errors });
    }, 180);

    return () => {
      window.clearTimeout(timer);
      if (sequence.current === requestId) sequence.current += 1;
    };
  }, [template?.id, bindingKey, datasetKey]);

  return {
    ...state,
    loadingCount: state.loadingIds.length,
    boundCount: template?.components.filter((component) => Boolean(bindings[component.id])).length ?? 0,
  };
};
