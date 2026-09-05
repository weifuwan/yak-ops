import {
  createWorkflowDefinition,
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  offlineWorkflowDefinition,
  onlineWorkflowDefinition,
  pauseWorkflowDefinition,
  resumeWorkflowDefinition,
  runWorkflowDefinition,
  updateWorkflowDefinition,
  type WorkflowDefinition,
} from '@/services/workflow/definitions';
import { history, useIntl } from '@umijs/max';
import { Modal, Pagination, Spin, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  WORKFLOW_PAGE_SIZE_OPTIONS,
  buildWorkflowSummary,
  isActiveRuntime,
  type WorkflowFilterKey,
  type WorkflowViewMode,
} from '../model';
import WorkflowCreateDrawer, { type WorkflowCreateValues } from './WorkflowCreateDrawer';
import WorkflowDefinitionCard from './WorkflowDefinitionCard';
import WorkflowEmptyState from './WorkflowEmptyState';
import WorkflowPageHeader from './WorkflowPageHeader';
import WorkflowSummaryCards from './WorkflowSummaryCards';
import WorkflowToolbar from './WorkflowToolbar';

const WorkflowDefinitionList = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<string>();
  const [filter, setFilter] = useState<WorkflowFilterKey>('ALL');
  const [keyword, setKeyword] = useState('');
  const [viewMode, setViewMode] = useState<WorkflowViewMode>('grid');
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const loadDefinitions = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await listWorkflowDefinitions();
      setDefinitions(data || []);
    } catch (error) {
      if (!silent) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.workflow.definition.loadFailed' }),
        );
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDefinitions();
  }, [loadDefinitions]);

  useEffect(() => {
    if (!definitions.some((item) => isActiveRuntime(item.latestExecutionStatus))) return;
    const timer = window.setInterval(() => void loadDefinitions(true), 1800);
    return () => window.clearInterval(timer);
  }, [definitions, loadDefinitions]);

  const filteredDefinitions = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return definitions.filter((item) => {
      if (filter !== 'ALL' && item.status !== filter) return false;
      if (!normalizedKeyword) return true;
      return (
        item.name.toLowerCase().includes(normalizedKeyword) ||
        (item.description || '').toLowerCase().includes(normalizedKeyword)
      );
    });
  }, [definitions, filter, keyword]);

  const summary = useMemo(() => buildWorkflowSummary(definitions), [definitions]);
  const hasActiveFilters = filter !== 'ALL' || Boolean(keyword.trim());

  useEffect(() => setPageNo(1), [filter, keyword]);
  useEffect(() => {
    const maxPage = Math.max(1, Math.ceil(filteredDefinitions.length / pageSize));
    if (pageNo > maxPage) setPageNo(maxPage);
  }, [filteredDefinitions.length, pageNo, pageSize]);

  const pageRecords = useMemo(() => {
    const start = (pageNo - 1) * pageSize;
    return filteredDefinitions.slice(start, start + pageSize);
  }, [filteredDefinitions, pageNo, pageSize]);

  const executeAction = async (
    id: string,
    action: () => Promise<WorkflowDefinition>,
    success: string,
  ) => {
    if (actionId) return;
    setActionId(id);
    try {
      await action();
      message.success(success);
      await loadDefinitions(true);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.common.operationFailed' }),
      );
    } finally {
      setActionId(undefined);
    }
  };

  const goToDefinition = (record: WorkflowDefinition) => {
    history.push(`/workflow/definition/${record.id}?scene=edit`);
  };
  const goToSchedules = (record: WorkflowDefinition) => {
    history.push(`/workflow/definition/${encodeURIComponent(record.id)}/schedule`);
  };

  const handleCreate = async (values: WorkflowCreateValues) => {
    setCreating(true);
    try {
      const created = await createWorkflowDefinition({
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
      });
      const configured = await updateWorkflowDefinition(created.id, {
        name: created.name,
        description: created.description,
        nodes: created.nodes,
        edges: created.edges,
        input: created.input,
        editorMeta: { ...created.editorMeta, icon: values.icon },
        workflowTimeoutSeconds: created.workflowTimeoutSeconds,
        failureStrategy: created.failureStrategy,
      });
      message.success(
        intlRef.current.formatMessage({ id: 'pages.workflow.definition.created' }),
      );
      setCreateOpen(false);
      history.push(`/workflow/definition/${configured.id}?scene=create`);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.definition.createFailed' }),
      );
      throw error;
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: 'pages.workflow.definition.deleteTitle' }),
      content: intl.formatMessage(
        { id: 'pages.workflow.definition.deleteContent' },
        { name: record.name },
      ),
      okText: intl.formatMessage({ id: 'pages.workflow.common.delete' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        if (actionId) return;
        setActionId(record.id);
        try {
          await deleteWorkflowDefinition(record.id);
          message.success(
            intlRef.current.formatMessage({ id: 'pages.workflow.definition.deleted' }),
          );
          await loadDefinitions(true);
        } catch (error) {
          message.error(
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({ id: 'pages.workflow.definition.deleteFailed' }),
          );
        } finally {
          setActionId(undefined);
        }
      },
    });
  };

  const handlePublish = (record: WorkflowDefinition) => {
    if (record.nodeCount <= 0) {
      message.warning(
        intl.formatMessage({ id: 'pages.workflow.definition.publishRequiresNode' }),
      );
      return;
    }
    const reenable =
      record.status === 'OFFLINE' && Boolean(record.activeVersionNo) && !record.draftChanged;
    const publishingUpdate = Boolean(record.activeVersionNo) && record.draftChanged;
    const targetVersionNo = reenable
      ? record.activeVersionNo || 1
      : (record.latestVersionNo || 0) + 1;
    const titleId = reenable
      ? 'pages.workflow.definition.reenableTitle'
      : publishingUpdate
        ? 'pages.workflow.definition.publishUpdateTitle'
        : 'pages.workflow.definition.publishTitle';
    const contentId = reenable
      ? 'pages.workflow.definition.reenableContent'
      : publishingUpdate
        ? 'pages.workflow.definition.publishUpdateContent'
        : 'pages.workflow.definition.publishContent';
    const okId = reenable
      ? 'pages.workflow.definition.reenable'
      : publishingUpdate
        ? 'pages.workflow.definition.publishUpdate'
        : 'pages.workflow.definition.publish';

    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: titleId }, { version: targetVersionNo }),
      content: intl.formatMessage({ id: contentId }, { version: targetVersionNo }),
      okText: intl.formatMessage({ id: okId }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      onOk: () =>
        executeAction(
          record.id,
          () => onlineWorkflowDefinition(record.id),
          reenable
            ? intlRef.current.formatMessage({ id: 'pages.workflow.definition.reenabled' })
            : intlRef.current.formatMessage(
                { id: 'pages.workflow.definition.published' },
                { version: targetVersionNo },
              ),
        ),
    });
  };

  const handleOffline = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: 'pages.workflow.definition.offlineTitle' }),
      content: intl.formatMessage({ id: 'pages.workflow.definition.offlineContent' }),
      okText: intl.formatMessage({ id: 'pages.workflow.definition.offline' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: () =>
        executeAction(
          record.id,
          () => offlineWorkflowDefinition(record.id),
          intlRef.current.formatMessage({ id: 'pages.workflow.definition.offlined' }),
        ),
    });
  };

  const handleRun = (record: WorkflowDefinition) => {
    if (
      record.status !== 'ONLINE' ||
      !record.activeVersionNo ||
      record.nodeCount <= 0 ||
      isActiveRuntime(record.latestExecutionStatus)
    ) return;

    Modal.confirm({
      centered: true,
      title: intl.formatMessage(
        { id: 'pages.workflow.definition.runTitle' },
        { version: record.activeVersionNo },
      ),
      content: intl.formatMessage(
        {
          id: record.draftChanged
            ? 'pages.workflow.definition.runDraftContent'
            : 'pages.workflow.definition.runContent',
        },
        { version: record.activeVersionNo },
      ),
      okText: intl.formatMessage({ id: 'pages.workflow.definition.run' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      onOk: () =>
        executeAction(
          record.id,
          () => runWorkflowDefinition(record.id),
          intlRef.current.formatMessage(
            { id: 'pages.workflow.definition.started' },
            { version: record.activeVersionNo || 0 },
          ),
        ),
    });
  };

  const handlePause = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: 'pages.workflow.definition.pauseTitle' }),
      content: intl.formatMessage({ id: 'pages.workflow.definition.pauseContent' }),
      okText: intl.formatMessage({ id: 'pages.workflow.definition.pause' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      onOk: () =>
        executeAction(
          record.id,
          () => pauseWorkflowDefinition(record.id),
          intlRef.current.formatMessage({ id: 'pages.workflow.definition.pauseRequested' }),
        ),
    });
  };

  const handleResume = (record: WorkflowDefinition) => {
    void executeAction(
      record.id,
      () => resumeWorkflowDefinition(record.id),
      intlRef.current.formatMessage({ id: 'pages.workflow.definition.resumed' }),
    );
  };

  const resetFilters = () => {
    setFilter('ALL');
    setKeyword('');
    setPageNo(1);
  };

  return (
    <>
      <div className="min-h-[calc(100dvh-64px)] bg-[#f7f8fa] text-[#242731]">
        <div className="flex min-h-[calc(100dvh-64px)] flex-col bg-white px-6 pb-4 pt-5 shadow-[0_2px_10px_rgba(31,35,41,0.025)] max-md:px-4" style={{ borderTopRightRadius: 20, borderTopLeftRadius: 20 }}>
          <div className="space-y-5">
            <WorkflowPageHeader onCreate={() => setCreateOpen(true)} />
            <WorkflowSummaryCards summary={summary} />
            <WorkflowToolbar
              filter={filter}
              keyword={keyword}
              viewMode={viewMode}
              hasActiveFilters={hasActiveFilters}
              onFilterChange={setFilter}
              onKeywordChange={setKeyword}
              onViewModeChange={setViewMode}
              onReset={resetFilters}
            />
          </div>

          <div className="mt-5 flex flex-1 flex-col">
            <Spin spinning={loading}>
              <div
                className={
                  viewMode === 'list'
                    ? 'grid grid-cols-1 gap-[14px]'
                    : 'grid grid-cols-1 gap-[14px] md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4'
                }
              >
                {pageRecords.map((record) => (
                  <WorkflowDefinitionCard
                    key={record.id}
                    record={record}
                    viewMode={viewMode}
                    busy={actionId === record.id}
                    blocked={Boolean(actionId && actionId !== record.id)}
                    onEdit={goToDefinition}
                    onSchedule={goToSchedules}
                    onDelete={handleDelete}
                    onPublish={handlePublish}
                    onOffline={handleOffline}
                    onRun={handleRun}
                    onPause={handlePause}
                    onResume={handleResume}
                  />
                ))}
              </div>
              {!loading && filteredDefinitions.length === 0 ? (
                <div className="mt-6">
                  <WorkflowEmptyState
                    filtered={hasActiveFilters}
                    onReset={resetFilters}
                    onCreate={() => setCreateOpen(true)}
                  />
                </div>
              ) : null}
            </Spin>

            {filteredDefinitions.length > 0 ? (
              <div className="mt-auto flex shrink-0 justify-end pt-6">
                <Pagination
                  current={pageNo}
                  pageSize={pageSize}
                  total={filteredDefinitions.length}
                  showSizeChanger
                  showQuickJumper
                  pageSizeOptions={WORKFLOW_PAGE_SIZE_OPTIONS}
                  disabled={loading}
                  showTotal={(total, range) =>
                    intl.formatMessage(
                      { id: 'pages.workflow.definition.pagination' },
                      { start: range[0], end: range[1], total },
                    )
                  }
                  onChange={(nextPage, nextPageSize) => {
                    setPageNo(nextPageSize !== pageSize ? 1 : nextPage);
                    setPageSize(nextPageSize);
                  }}
                />
              </div>
            ) : null}
          </div>
        </div>
      </div>

      <WorkflowCreateDrawer
        open={createOpen}
        creating={creating}
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
      />
    </>
  );
};

export default WorkflowDefinitionList;
