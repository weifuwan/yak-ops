import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  PageHeader,
  Table,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  alertChannelTypeLabel,
  deleteAlertChannel,
  deleteAlertGroup,
  listAlertChannels,
  listAlertGroups,
  listAlertPluginDefinitions,
  parseAlertInstanceIds,
  testAlertChannel,
  type AlertChannelRecord,
  type AlertGroupRecord,
} from "@/service/alert";

import { AlertChannelForm } from "./channel-form";
import { AlertGroupForm } from "./group-form";

type AlertTab = "channels" | "groups";
type PendingDelete =
  | { kind: "channel"; record: AlertChannelRecord }
  | { kind: "group"; record: AlertGroupRecord };

const queryKeys = {
  channels: (userId: string) => ["management", "alerts", "channels", userId] as const,
  groups: (userId: string) => ["management", "alerts", "groups", userId] as const,
  plugins: (userId: string) => ["management", "alerts", "plugins", userId] as const,
};

const displayTime = (value?: string) => value || "-";

export function AlertCenterPage() {
  const { currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: AlertTab = searchParams.get("tab") === "groups" ? "groups" : "channels";
  const [keyword, setKeyword] = useState("");
  const [channelFormOpen, setChannelFormOpen] = useState(false);
  const [groupFormOpen, setGroupFormOpen] = useState(false);
  const [editingChannel, setEditingChannel] = useState<AlertChannelRecord>();
  const [editingGroup, setEditingGroup] = useState<AlertGroupRecord>();
  const [pendingDelete, setPendingDelete] = useState<PendingDelete>();
  const [deleting, setDeleting] = useState(false);
  const [testingId, setTestingId] = useState<number>();
  const userId = currentUser?.id ?? "";

  const channelsQuery = useQuery({
    queryKey: queryKeys.channels(userId),
    queryFn: listAlertChannels,
    enabled: Boolean(userId),
  });
  const groupsQuery = useQuery({
    queryKey: queryKeys.groups(userId),
    queryFn: listAlertGroups,
    enabled: Boolean(userId),
  });
  const pluginsQuery = useQuery({
    queryKey: queryKeys.plugins(userId),
    queryFn: listAlertPluginDefinitions,
    enabled: Boolean(userId),
  });

  const channels = channelsQuery.data ?? [];
  const groups = groupsQuery.data ?? [];
  const normalizedKeyword = keyword.trim().toLowerCase();

  const filteredChannels = useMemo(
    () =>
      normalizedKeyword
        ? channels.filter((record) =>
            [record.instanceName, record.alertPluginName]
              .filter(Boolean)
              .some((value) => String(value).toLowerCase().includes(normalizedKeyword)),
          )
        : channels,
    [channels, normalizedKeyword],
  );

  const filteredGroups = useMemo(
    () =>
      normalizedKeyword
        ? groups.filter((record) =>
            [record.groupName, record.description]
              .filter(Boolean)
              .some((value) => String(value).toLowerCase().includes(normalizedKeyword)),
          )
        : groups,
    [groups, normalizedKeyword],
  );

  const channelNameById = useMemo(
    () => new Map(channels.map((channel) => [channel.id, channel.instanceName])),
    [channels],
  );

  const refreshChannels = async () => {
    await queryClient.invalidateQueries({ queryKey: ["management", "alerts", "channels"] });
  };
  const refreshGroups = async () => {
    await queryClient.invalidateQueries({ queryKey: ["management", "alerts", "groups"] });
  };

  const openCreate = () => {
    if (tab === "channels") {
      setEditingChannel(undefined);
      setChannelFormOpen(true);
    } else {
      setEditingGroup(undefined);
      setGroupFormOpen(true);
    }
  };

  const runTest = async (record: AlertChannelRecord) => {
    if (testingId) return;
    setTestingId(record.id);
    try {
      await testAlertChannel(record.pluginDefineId, record.pluginInstanceParams);
      toast.success("测试消息发送成功");
    } finally {
      setTestingId(undefined);
    }
  };

  const performDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    try {
      if (pendingDelete.kind === "channel") {
        await deleteAlertChannel(pendingDelete.record.id);
        toast.success("通知渠道已删除");
        await refreshChannels();
      } else {
        await deleteAlertGroup(pendingDelete.record.id);
        toast.success("通知组已删除");
        await refreshGroups();
      }
      setPendingDelete(undefined);
    } finally {
      setDeleting(false);
    }
  };

  const channelColumns: TableColumns<AlertChannelRecord> = [
    {
      key: "name",
      title: "名称",
      dataIndex: "instanceName",
      minWidth: 220,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.instanceName || "-"}
        </span>
      ),
    },
    {
      key: "type",
      title: "类型",
      dataIndex: "alertPluginName",
      width: 160,
      render: (_value, record) => alertChannelTypeLabel(record.alertPluginName),
    },
    {
      key: "updateTime",
      title: "更新时间",
      dataIndex: "updateTime",
      width: 180,
      render: (_value, record) => displayTime(record.updateTime),
    },
    {
      key: "actions",
      title: "操作",
      width: 190,
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            className="px-1 text-xs font-normal"
            onClick={() => {
              setEditingChannel(record);
              setChannelFormOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="px-1 text-xs font-normal"
            isPending={testingId === record.id}
            onClick={() => void runTest(record)}
          >
            测试
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="px-1 text-xs font-normal hover:text-[var(--yak-color-danger)]"
            onClick={() => setPendingDelete({ kind: "channel", record })}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];

  const groupColumns: TableColumns<AlertGroupRecord> = [
    {
      key: "name",
      title: "名称",
      dataIndex: "groupName",
      minWidth: 200,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.groupName || "-"}
        </span>
      ),
    },
    {
      key: "channels",
      title: "通知渠道",
      minWidth: 260,
      render: (_value, record) => {
        const names = parseAlertInstanceIds(record.alertInstanceIds)
          .map((id) => channelNameById.get(id))
          .filter(Boolean);
        return names.length ? names.join("、") : "-";
      },
    },
    {
      key: "description",
      title: "描述",
      dataIndex: "description",
      minWidth: 240,
      ellipsis: true,
      render: (_value, record) => record.description || "-",
    },
    {
      key: "updateTime",
      title: "更新时间",
      dataIndex: "updateTime",
      width: 180,
      render: (_value, record) => displayTime(record.updateTime),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button
            size="sm"
            variant="ghost"
            className="px-1 text-xs font-normal"
            onClick={() => {
              setEditingGroup(record);
              setGroupFormOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            size="sm"
            variant="ghost"
            isDisabled={record.id === 1}
            title={record.id === 1 ? "默认通知组不能删除" : undefined}
            className="px-1 text-xs font-normal hover:text-[var(--yak-color-danger)]"
            onClick={() => setPendingDelete({ kind: "group", record })}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];

  const activeQuery = tab === "channels" ? channelsQuery : groupsQuery;
  const loading = activeQuery.isPending || activeQuery.isFetching;

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="告警中心" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <Tabs
              value={tab}
              onValueChange={(value) => {
                const next = value === "groups" ? "groups" : "channels";
                setKeyword("");
                setSearchParams(next === "groups" ? { tab: "groups" } : {}, { replace: true });
              }}
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <TabsList>
                  <TabsTab value="channels">通知渠道</TabsTab>
                  <TabsTab value="groups">通知组</TabsTab>
                </TabsList>
                <div className="flex items-center gap-2">
                  <div className="w-[260px]">
                    <Input
                      size="small"
                      variant="outlined"
                      value={keyword}
                      placeholder={tab === "channels" ? "搜索名称或类型" : "搜索名称或描述"}
                      onChange={(event) => setKeyword(event.target.value)}
                    />
                  </div>
                  <Button size="sm" variant="primary" onClick={openCreate}>
                    <Plus size={14} />
                    {tab === "channels" ? "新建通知渠道" : "新建通知组"}
                  </Button>
                </div>
              </div>

              {activeQuery.isError ? (
                <div
                  role="alert"
                  className="mt-4 flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
                >
                  数据加载失败
                  <Button size="sm" variant="secondary" onClick={() => void activeQuery.refetch()}>
                    重试
                  </Button>
                </div>
              ) : null}

              <TabsPanel value="channels" className="mt-4 min-h-0 flex-1">
                <Table<AlertChannelRecord>
                  className="min-h-full"
                  columns={channelColumns}
                  dataSource={filteredChannels}
                  rowKey={(record) => String(record.id)}
                  loading={loading}
                  bordered
                  size="medium"
                  scroll={{ x: 850 }}
                  emptyText={normalizedKeyword ? "未找到匹配通知渠道" : "暂无通知渠道"}
                  pagination={false}
                />
              </TabsPanel>

              <TabsPanel value="groups" className="mt-4 min-h-0 flex-1">
                <Table<AlertGroupRecord>
                  className="min-h-full"
                  columns={groupColumns}
                  dataSource={filteredGroups}
                  rowKey={(record) => String(record.id)}
                  loading={loading}
                  bordered
                  size="medium"
                  scroll={{ x: 1050 }}
                  emptyText={normalizedKeyword ? "未找到匹配通知组" : "暂无通知组"}
                  pagination={false}
                />
              </TabsPanel>
            </Tabs>
          </div>
        </div>
      </div>

      <AlertChannelForm
        open={channelFormOpen}
        record={editingChannel}
        plugins={pluginsQuery.data ?? []}
        onClose={() => setChannelFormOpen(false)}
        onSaved={() => {
          toast.success(editingChannel ? "通知渠道保存成功" : "通知渠道创建成功");
          void refreshChannels();
        }}
      />

      <AlertGroupForm
        open={groupFormOpen}
        record={editingGroup}
        channels={channels}
        onClose={() => setGroupFormOpen(false)}
        onSaved={() => {
          toast.success(editingGroup ? "通知组保存成功" : "通知组创建成功");
          void refreshGroups();
        }}
      />

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !deleting) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">
            {pendingDelete?.kind === "channel" ? "删除通知渠道" : "删除通知组"}
          </DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除“
            {pendingDelete?.kind === "channel"
              ? pendingDelete.record.instanceName
              : pendingDelete?.record.groupName}
            ”吗？
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={deleting}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="danger"
              isPending={deleting}
              onClick={() => void performDelete()}
            >
              删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
