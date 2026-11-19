import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  Modal,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";
import { Copy, Plus, RefreshCw } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  deleteAccessToken,
  listAccessTokens,
  type AccessTokenPageParams,
  type AccessTokenRecord,
} from "@/service/access-token";

import { AccessTokenForm } from "./token-form";
import { AccessTokenTable } from "./token-table";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

const readPositiveNumber = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return value && Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

const accessTokenQueryKeys = {
  all: ["management", "access-tokens"] as const,
  page: (userId: string, params: AccessTokenPageParams) =>
    [
      ...accessTokenQueryKeys.all,
      userId,
      params.pageNo,
      params.pageSize,
      params.searchVal ?? "",
    ] as const,
};

export function AccessTokenManagementPage() {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const pageNo = readPositiveNumber(searchParams.get("page"), 1);
  const requestedSize = readPositiveNumber(searchParams.get("size"), 10);
  const pageSize = PAGE_SIZE_OPTIONS.includes(requestedSize) ? requestedSize : 10;
  const searchVal = searchParams.get("search") ?? "";
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<AccessTokenRecord>();
  const [pendingDelete, setPendingDelete] = useState<AccessTokenRecord>();
  const [issuedToken, setIssuedToken] = useState<{ value: string; title: string }>();

  const page = useQuery({
    queryKey: accessTokenQueryKeys.page(userId, {
      pageNo,
      pageSize,
      searchVal: searchVal.trim() || undefined,
    }),
    queryFn: () =>
      listAccessTokens({
        pageNo,
        pageSize,
        searchVal: searchVal.trim() || undefined,
      }),
    enabled: Boolean(userId),
  });

  const records = page.data?.records ?? [];
  const total = page.data?.total ?? 0;
  const loading = page.isPending || page.isFetching;

  const updateParams = (changes: Record<string, string | undefined>) => {
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        return next;
      },
      { replace: true },
    );
  };

  useEffect(() => {
    if (page.isSuccess && records.length === 0 && total > 0 && pageNo > 1) {
      updateParams({ page: String(pageNo - 1) });
    }
  }, [page.isSuccess, records.length, total, pageNo]);

  const remove = useMutation({
    mutationFn: deleteAccessToken,
    onSuccess: async () => {
      toast.success("Token 删除成功");
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: accessTokenQueryKeys.all });
    },
  });

  const copyToken = async (token: string) => {
    try {
      await navigator.clipboard.writeText(token);
      toast.success("Token 已复制");
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    }
  };

  const openCreate = () => {
    setEditingRecord(undefined);
    setFormOpen(true);
  };

  const openEdit = (record: AccessTokenRecord) => {
    setEditingRecord(record);
    setFormOpen(true);
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="Token 管理" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 flex-wrap items-center gap-2">
              <Button size="sm" variant="primary" onClick={openCreate}>
                <Plus size={14} />
                新增 Token
              </Button>
              <div className="relative w-[280px]">
                <Input
                  size="small"
                  variant="outlined"
                  value={searchVal}
                  placeholder="搜索用户名"
                  onChange={(event) => updateParams({ search: event.target.value, page: undefined })}
                />
              </div>
              <div className="ml-auto">
                <Button
                  size="sm"
                  variant="secondary"
                  isDisabled={loading}
                  onClick={() => void page.refetch()}
                >
                  <RefreshCw size={13} />
                  刷新
                </Button>
              </div>
            </section>

            <section className="mt-4 min-h-0 flex-1">
              {page.isError ? (
                <div
                  role="alert"
                  className="mb-3 flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
                >
                  Token 加载失败
                  <Button variant="secondary" size="sm" onClick={() => void page.refetch()}>
                    重试
                  </Button>
                </div>
              ) : null}
              <AccessTokenTable
                records={records}
                loading={loading}
                pageNo={pageNo}
                pageSize={pageSize}
                total={total}
                filtered={Boolean(searchVal.trim())}
                deleting={remove.isPending}
                onCopy={(token) => void copyToken(token)}
                onEdit={openEdit}
                onDelete={setPendingDelete}
                onPageChange={(nextPage, nextSize) =>
                  updateParams({ page: String(nextPage), size: String(nextSize) })
                }
              />
            </section>
          </div>
        </div>
      </div>

      <AccessTokenForm
        open={formOpen}
        record={editingRecord}
        onClose={() => setFormOpen(false)}
        onSaved={(record) => {
          toast.success(editingRecord ? "Token 更新成功，旧 Token 已失效" : "Token 创建成功");
          setIssuedToken({
            value: record.token,
            title: editingRecord ? "Token 已更新" : "Token 创建成功",
          });
          void queryClient.invalidateQueries({ queryKey: accessTokenQueryKeys.all });
        }}
      />

      <Modal
        open={Boolean(issuedToken)}
        centered
        width={560}
        title={issuedToken?.title ?? "Token"}
        onClose={() => setIssuedToken(undefined)}
        footer={
          <Button size="sm" variant="primary" onClick={() => setIssuedToken(undefined)}>
            完成
          </Button>
        }
      >
        <div className="space-y-3">
          <p className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
            请复制并保存此 Token。它用于调用 Yak-OPS API，请勿将其暴露在公开代码或浏览器客户端中。
          </p>
          <div className="flex items-center gap-2">
            <Input
              size="small"
              variant="outlined"
              value={issuedToken?.value ?? ""}
              readOnly
              className="font-mono"
            />
            <Button
              size="sm"
              variant="secondary"
              isDisabled={!issuedToken?.value}
              onClick={() => {
                if (issuedToken?.value) void copyToken(issuedToken.value);
              }}
            >
              <Copy size={13} />
              复制
            </Button>
          </div>
        </div>
      </Modal>

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">删除 Token</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除用户“{pendingDelete?.userName || "用户 ID：" + pendingDelete?.userId}”的 Token 吗？删除后，使用该 Token 的 API 请求将立即失效。
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              variant="secondary"
              size="sm"
              isDisabled={remove.isPending}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="danger"
              isPending={remove.isPending}
              onClick={() => {
                if (pendingDelete) remove.mutate(pendingDelete.id);
              }}
            >
              删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
