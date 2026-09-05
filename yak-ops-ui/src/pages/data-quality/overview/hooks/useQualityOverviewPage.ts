import {
  getQualityOverview,
  type QualityOverviewView,
} from '@/services/data-quality';
import { useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  rangeKey,
  resolvePresetRange,
  type OverviewDateRange,
} from '../utils';

export const useQualityOverviewPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const radarRange = useMemo(() => resolvePresetRange('7d'), []);
  const [qualityRange, setQualityRange] = useState<OverviewDateRange>(() =>
    resolvePresetRange('yesterday'),
  );
  const [issueRange, setIssueRange] = useState<OverviewDateRange>(() =>
    resolvePresetRange('7d'),
  );
  const cacheRef = useRef(new Map<string, QualityOverviewView>());
  const inFlightRef = useRef(new Map<string, Promise<QualityOverviewView>>());
  const [loadingKeys, setLoadingKeys] = useState<Set<string>>(() => new Set());
  const [, setRevision] = useState(0);

  const loadRange = useCallback(
    async (range: OverviewDateRange, force = false) => {
      const key = rangeKey(range);
      if (!force && cacheRef.current.has(key)) return cacheRef.current.get(key);

      const existing = inFlightRef.current.get(key);
      if (existing && !force) return existing;

      setLoadingKeys((previous) => new Set(previous).add(key));
      const request = getQualityOverview(range);
      inFlightRef.current.set(key, request);

      try {
        const overview = await request;
        cacheRef.current.set(key, overview);
        setRevision((value) => value + 1);
        return overview;
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataQuality.overview.loadFailed',
              }),
        );
        return undefined;
      } finally {
        if (inFlightRef.current.get(key) === request) {
          inFlightRef.current.delete(key);
        }
        setLoadingKeys((previous) => {
          const next = new Set(previous);
          next.delete(key);
          return next;
        });
      }
    },
    [],
  );

  useEffect(() => {
    const uniqueRanges = new Map<string, OverviewDateRange>();
    [radarRange, qualityRange, issueRange].forEach((range) =>
      uniqueRanges.set(rangeKey(range), range),
    );
    uniqueRanges.forEach((range) => void loadRange(range));
  }, [issueRange, loadRange, qualityRange, radarRange]);

  const overviewFor = useCallback(
    (range: OverviewDateRange) => cacheRef.current.get(rangeKey(range)),
    [],
  );
  const loadingFor = useCallback(
    (range: OverviewDateRange) => loadingKeys.has(rangeKey(range)),
    [loadingKeys],
  );

  return {
    radarRange,
    radarOverview: overviewFor(radarRange),
    radarLoading: loadingFor(radarRange),
    qualityRange,
    qualityOverview: overviewFor(qualityRange),
    qualityLoading: loadingFor(qualityRange),
    setQualityRange,
    issueRange,
    issueOverview: overviewFor(issueRange),
    issueLoading: loadingFor(issueRange),
    setIssueRange,
    refresh: (range: OverviewDateRange) => loadRange(range, true),
  };
};
