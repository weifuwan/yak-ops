import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  PageHeader,
  toast,
} from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import {
  deleteTenant,
  listTenantsPage,
  type TenantPageParams,
  type TenantRecord,
} from "@/service/tenant";

import { TenantFilters } from "./tenant-filters";
import { TenantForm } from "./tenant-form";
import { TenantTable } from "./tenant-table";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

const tenantQueryKeys = {
  all: ["management", "tenants"] as const,
  page: (userId: string, params: TenantPageParams) =>
    [
      ...tenantQueryKeys.all,
      userId,
      params.pageNo,
      params.pageSize,
      params.searchVal ?? "",
    ] as const,
};

const readPositiveNumber = (value: string | null, fallback: number) => {
  const parsed = Number(value);
  return value && Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

export function TenantManagementPage() {
  const { currentUser } = useAuth();
  const canManage = currentUser?.userType === "ADMIN_USER";
  const userId = currentUser?.id ?? "";
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const pageNo = readPositiveNumber(searchParams.get("page"), 1);
  const requestedSize = readPositiveNumber(searchParams.get("size"), 10);
  const pageSize = PAGE_SIZE_OPTIONS.includes(requestedSize) ? requestedSize : 10;
  const searchVal = searchParams.get("search") ?? "";
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<TenantRecord>();
  const [pendingDelete, setPendingDelete] = useState<TenantRecord>();

  const page = useQuery({
    queryKey: tenantQueryKeys.page(userId, {
      pageNo,
      pageSize,
      searchVal: searchVal.trim() || undefined,
    }),
    queryFn: () =>
      listTenantsPage({
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
    mutationFn: deleteTenant,
    onSuccess: async () => {
      toast.success("租户删除成功");
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: tenantQueryKeys.all });
    },
  });

  const openCreate = () => {
    setEditingRecord(undefined);
    setFormOpen(true);
  };

  const openEdit = (record: TenantRecord) => {
    setEditingRecord(record);
    setFormOpen(true);
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="租户管理" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <TenantFilters
              searchVal={searchVal}
              canCreate={canManage}
              onCreate={openCreate}
              onSearchChange={(value) => updateParams({ search: value, page: undefined })}
            />
            <section className="mt-4 min-h-0 flex-1">
              {page.isError ? (
                <div
                  role="alert"
                  className="mb-3 flex items-center gap-3 text-sm text-[var(--yak-components-muted-text)]"
                >
                  租户加载失败
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void page.refetch()}
                  >
                    重试
                  </Button>
                </div>
              ) : null}
              <TenantTable
                records={records}
                loading={loading}
                pageNo={pageNo}
                pageSize={pageSize}
                total={total}
                filtered={Boolean(searchVal.trim())}
                canManage={canManage}
                deleting={remove.isPending}
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

      {canManage ? (
        <TenantForm
          open={formOpen}
          record={editingRecord}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            toast.success(editingRecord ? "租户保存成功" : "租户创建成功");
            void queryClient.invalidateQueries({ queryKey: tenantQueryKeys.all });
          }}
        />
      ) : null}

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !remove.isPending) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">删除租户</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除租户“{pendingDelete?.tenantCode || "-"}”吗？存在关联用户、调度定义或运行中工作流时，后端会阻止删除。
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
