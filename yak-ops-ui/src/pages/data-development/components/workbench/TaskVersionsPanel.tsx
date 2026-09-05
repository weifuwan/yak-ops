import { API_SUCCESS_CODE } from '@/services/http/response';
import { useIntl } from '@umijs/max';
import { Spin, message } from 'antd';
import { FileCode2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import {
  getDevelopmentTaskRevision,
  listDevelopmentTaskRevisions,
} from '../../service';
import type {
  DevelopmentNode,
  DevelopmentTaskRevision,
  DevelopmentTaskRevisionSummary,
} from '../../types';

interface TaskVersionsPanelProps {
  node: DevelopmentNode;
  refreshKey: number;
}

const responseData = <T,>(
  response: { code?: number; data?: T; msg?: string; message?: string },
  fallback: string,
): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const TaskVersionsPanel = ({ node, refreshKey }: TaskVersionsPanelProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const [versions, setVersions] = useState<DevelopmentTaskRevisionSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<DevelopmentTaskRevision>();

  const text = (id: string, values?: Record<string, string | number>) =>
    intlRef.current.formatMessage({ id }, values);
  const formatTime = (value?: string) => {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? value
      : date.toLocaleString(intl.locale, { hour12: false });
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setDetail(undefined);
    listDevelopmentTaskRevisions(node.id)
      .then((response) => {
        if (!active) return;
        setVersions(
          responseData(
            response,
            text('pages.dataDevelopment.versions.queryFailed'),
          ) || [],
        );
      })
      .catch((error) => {
        if (active) {
          message.error(
            error instanceof Error
              ? error.message
              : text('pages.dataDevelopment.versions.queryFailed'),
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [node.id, refreshKey]);

  const openDetail = async (revisionNo: number) => {
    setDetailLoading(true);
    try {
      setDetail(
        responseData(
          await getDevelopmentTaskRevision(node.id, revisionNo),
          text('pages.dataDevelopment.versions.detailFailed'),
        ),
      );
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.versions.detailFailed'),
      );
    } finally {
      setDetailLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-24 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  if (!versions.length) {
    return (
      <div className="py-8 text-center text-[11px] leading-5 text-[#98a2b3]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.versions.empty' })}
        <div className="mt-1">
          {intl.formatMessage({ id: 'pages.dataDevelopment.versions.emptyHint' })}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-3 text-[12px]">
      <div className="space-y-1.5">
        {versions.map((version, index) => (
          <button
            key={version.id}
            type="button"
            onClick={() => void openDetail(version.revisionNo)}
            className="flex w-full items-center gap-2 rounded-[3px] border border-[#eaecf0] px-2.5 py-2 text-left transition-colors hover:bg-[#f8f9fa]"
          >
            <FileCode2 size={14} className="shrink-0 text-[#667085]" strokeWidth={1.7} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-[#344054]">v{version.revisionNo}</span>
                {index === 0 ? (
                  <span className="rounded bg-[#f2f4f7] px-1.5 py-0.5 text-[10px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.dataDevelopment.versions.latest' })}
                  </span>
                ) : null}
              </div>
              <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">
                {formatTime(version.createTime)} · {version.checksum.slice(0, 10)}
              </div>
            </div>
          </button>
        ))}
      </div>

      {detailLoading ? (
        <div className="flex h-16 items-center justify-center border-t border-[#eef0f2]">
          <Spin size="small" />
        </div>
      ) : detail ? (
        <div className="border-t border-[#eef0f2] pt-3">
          <div className="flex items-center justify-between gap-3">
            <span className="font-medium text-[#344054]">
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.versions.content' },
                { revision: detail.revisionNo },
              )}
            </span>
            <span className="text-[10px] text-[#98a2b3]">
              Draft #{detail.sourceDraftRevision}
            </span>
          </div>
          <pre className="mt-2 max-h-[300px] overflow-auto whitespace-pre-wrap break-words rounded-[3px] bg-[#f8f9fa] p-2.5 font-mono text-[11px] leading-5 text-[#475467]">
            {detail.definition.content ||
              intl.formatMessage({ id: 'pages.dataDevelopment.common.emptyContent' })}
          </pre>
          <div className="mt-2 break-all font-mono text-[9px] leading-4 text-[#b0b7c3]">
            SHA-256 {detail.checksum}
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default TaskVersionsPanel;
