import {
  createWorkflowDefinition,
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  offlineWorkflowDefinition,
  onlineWorkflowDefinition,
  pauseWorkflowDefinition,
  resumeWorkflowDefinition,
  runWorkflowDefinition,
  type WorkflowDefinition,
} from '@/services/workflow/definitions';
import { history } from '@umijs/max';
import { Modal, Pagination, Spin, message } from 'antd';
import { motion } from 'framer-motion';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  WORKFLOW_PAGE_ANIMATION,
  WORKFLOW_PAGE_SIZE_OPTIONS,
  buildWorkflowSummary,
  isActiveRuntime,
  type WorkflowFilterKey,
  type WorkflowViewMode,
} from '../model';
import WorkflowCreateDrawer, {
  type WorkflowCreateValues,
} from './WorkflowCreateDrawer';
import WorkflowDefinitionCard from './WorkflowDefinitionCard';
import WorkflowEmptyState from './WorkflowEmptyState';
import WorkflowPageHeader from './WorkflowPageHeader';
import WorkflowSummaryCards from './WorkflowSummaryCards';
import WorkflowToolbar from './WorkflowToolbar';

const WorkflowDefinitionList = () => {
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
          error instanceof Error ? error.message : '工作流加载失败',
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
    if (
      !definitions.some((item) =>
        isActiveRuntime(item.latestExecutionStatus),
      )
    ) {
      return;
    }

    const timer = window.setInterval(() => {
      void loadDefinitions(true);
    }, 1800);

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

  const summary = useMemo(
    () => buildWorkflowSummary(definitions),
    [definitions],
  );

  const hasActiveFilters = filter !== 'ALL' || Boolean(keyword.trim());

  useEffect(() => {
    setPageNo(1);
  }, [filter, keyword]);

  useEffect(() => {
    const maxPage = Math.max(
      1,
      Math.ceil(filteredDefinitions.length / pageSize),
    );
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
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setActionId(undefined);
    }
  };

  const goToDefinition = (record: WorkflowDefinition) => {
    history.push(`/workflow/definition/${record.id}?scene=edit`);
  };

  const goToSchedules = (record: WorkflowDefinition) => {
    history.push(
      `/workflow/definition/${encodeURIComponent(record.id)}/schedule`,
    );
  };

  const handleCreate = async (values: WorkflowCreateValues) => {
    setCreating(true);
    try {
      const created = await createWorkflowDefinition({
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
      });

      message.success('工作流草稿已创建，请继续配置任务节点');
      setCreateOpen(false);
      history.push(`/workflow/definition/${created.id}?scene=create`);
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : '创建工作流失败',
      );
      throw error;
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: '确认删除该工作流吗？',
      content: (
        <span>
          即将删除工作流
          <span className="font-semibold text-[#fe2c55]"> [{record.name}]</span>
          。
          <br />
          删除后无法恢复，请谨慎操作。
        </span>
      ),
      okText: '删除',
      cancelText: '取消',
      okType: 'primary',
      okButtonProps: { size: 'small', danger: true },
      cancelButtonProps: { size: 'small' },
      maskClosable: true,
      async onOk() {
        if (actionId) return;
        setActionId(record.id);
        try {
          await deleteWorkflowDefinition(record.id);
          message.success('工作流已删除');
          await loadDefinitions(true);
        } catch (error) {
          message.error(
            error instanceof Error ? error.message : '删除工作流失败',
          );
        } finally {
          setActionId(undefined);
        }
      },
    });
  };

  const handlePublish = (record: WorkflowDefinition) => {
    if (record.nodeCount <= 0) {
      message.warning('请先编辑工作流并添加至少一个任务节点');
      return;
    }

    const reenable =
      record.status === 'OFFLINE' &&
      Boolean(record.activeVersionNo) &&
      !record.draftChanged;
    const publishingUpdate =
      Boolean(record.activeVersionNo) && record.draftChanged;
    const targetVersionNo = reenable
      ? record.activeVersionNo
      : (record.latestVersionNo || 0) + 1;

    Modal.confirm({
      centered: true,
      title: reenable
        ? `重新上线工作流 v${targetVersionNo}？`
        : publishingUpdate
          ? `发布更新 v${targetVersionNo} 并上线？`
          : `发布并上线工作流 v${targetVersionNo}？`,
      content: reenable
        ? `将重新启用已发布的 v${targetVersionNo}，不会创建新版本，已保存调度将恢复触发。`
        : publishingUpdate
          ? `当前草稿将形成不可变的 v${targetVersionNo} 并成为正式运行版本；已有运行实例不会受到影响。`
          : `当前草稿将形成不可变的 v${targetVersionNo} 并开启正式运行入口；后续草稿修改不会影响该版本。`,
      okText: reenable
        ? '重新上线'
        : publishingUpdate
          ? '发布更新并上线'
          : '发布并上线',
      cancelText: '取消',
      onOk: () =>
        executeAction(
          record.id,
          () => onlineWorkflowDefinition(record.id),
          reenable
            ? '工作流已重新上线'
            : `工作流 v${targetVersionNo} 已发布并上线`,
        ),
    });
  };

  const handleOffline = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: '下线工作流',
      content:
        '下线后将关闭新的正式运行和调度触发；已经启动的实例继续执行，草稿仍可继续编辑和测试。确认下线吗？',
      okText: '下线',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () =>
        executeAction(
          record.id,
          () => offlineWorkflowDefinition(record.id),
          '工作流已下线',
        ),
    });
  };

  const handleRun = (record: WorkflowDefinition) => {
    if (
      record.status !== 'ONLINE' ||
      !record.activeVersionNo ||
      record.nodeCount <= 0 ||
      isActiveRuntime(record.latestExecutionStatus)
    ) {
      return;
    }

    Modal.confirm({
      centered: true,
      title: `运行已上线 v${record.activeVersionNo}？`,
      content: record.draftChanged
        ? `当前存在未发布草稿，本次仍运行已上线的 v${record.activeVersionNo}。`
        : '本次运行当前生效的正式版本。',
      okText: '运行',
      cancelText: '取消',
      onOk: () =>
        executeAction(
          record.id,
          () => runWorkflowDefinition(record.id),
          `工作流 v${record.activeVersionNo} 已启动`,
        ),
    });
  };

  const handlePause = (record: WorkflowDefinition) => {
    Modal.confirm({
      centered: true,
      title: '暂停最近执行？',
      content: '暂停只影响当前执行实例，不影响工作流草稿和已发布版本。',
      okText: '暂停',
      cancelText: '取消',
      onOk: () =>
        executeAction(
          record.id,
          () => pauseWorkflowDefinition(record.id),
          '已请求暂停工作流',
        ),
    });
  };

  const handleResume = (record: WorkflowDefinition) => {
    void executeAction(
      record.id,
      () => resumeWorkflowDefinition(record.id),
      '最近执行已恢复',
    );
  };

  const resetFilters = () => {
    setFilter('ALL');
    setKeyword('');
    setPageNo(1);
  };

  return (
    <>
      <div className="min-h-[calc(100dvh-56px)] bg-[#f7f8fa] text-[#242731]">
        <motion.main
          initial="hidden"
          animate="visible"
          variants={WORKFLOW_PAGE_ANIMATION.sectionStagger}
          className="px-4 pb-4 pt-4"
        >
          <motion.section
            variants={WORKFLOW_PAGE_ANIMATION.fadeUp}
            className="flex min-h-[calc(100dvh-88px)] flex-col rounded-[20px] bg-white px-6 pb-4 pt-5 shadow-[0_2px_10px_rgba(31,35,41,0.025)] max-md:px-4"
          >
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
                <motion.section
                  variants={WORKFLOW_PAGE_ANIMATION.cardStagger}
                  initial="hidden"
                  animate="visible"
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
                </motion.section>

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
                <motion.footer
                  variants={WORKFLOW_PAGE_ANIMATION.fadeUp}
                  className="mt-auto flex shrink-0 justify-end pt-6"
                >
                  <Pagination
                    current={pageNo}
                    pageSize={pageSize}
                    total={filteredDefinitions.length}
                    showSizeChanger
                    showQuickJumper
                    pageSizeOptions={WORKFLOW_PAGE_SIZE_OPTIONS}
                    disabled={loading}
                    showTotal={(total, range) =>
                      `第 ${range[0]}-${range[1]} 条，共 ${total} 条`
                    }
                    onChange={(nextPage, nextPageSize) => {
                      setPageNo(nextPageSize !== pageSize ? 1 : nextPage);
                      setPageSize(nextPageSize);
                    }}
                  />
                </motion.footer>
              ) : null}
            </div>
          </motion.section>
        </motion.main>
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
