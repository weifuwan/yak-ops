import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown } from "lucide-react";
import {
  Button, Checkbox, Popover, PopoverContent, PopoverTrigger, Table, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import type { DataDomainRecord } from "../common-layer/types";
import { planningQueryKeys } from "../query-keys";

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";

export function BusinessTypeDomainRelation({ projectCode, businessTypeId }: { projectCode: string; businessTypeId: string }) {
  const queryClient = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const domainsQuery = useQuery({
    queryKey: planningQueryKeys.dataDomainAll(projectCode),
    queryFn: () => dataDomainApi.all<DataDomainRecord>(projectCode),
    enabled: Boolean(projectCode),
  });
  const relationKey = planningQueryKeys.businessTypeRelatedDomains(projectCode, businessTypeId);
  const relatedQuery = useQuery({
    queryKey: relationKey,
    queryFn: () => businessTypeApi.selectRelatedDomains<DataDomainRecord>(projectCode, businessTypeId),
    enabled: Boolean(projectCode && businessTypeId),
  });
  const related = relatedQuery.data || [];
  const relatedIds = useMemo(() => new Set(related.map((item) => item.id).filter(Boolean) as string[]), [related]);
  useEffect(() => setSelectedIds(Array.from(relatedIds)), [relatedIds]);

  const addSelected = async () => {
    const newIds = selectedIds;
    if (!newIds.length) { setPickerOpen(false); return; }
    setSaving(true);
    try {
      await businessTypeApi.updateRelatedDomains(projectCode, businessTypeId, newIds);
      toast.success("数据域关联已更新");
      await queryClient.invalidateQueries({ queryKey: relationKey });
      setPickerOpen(false);
    } catch {
      toast.warning("关联数据域失败，请刷新后重试");
    } finally { setSaving(false); }
  };
  const removeDomain = async (id: string) => {
    try {
      await businessTypeApi.deleteRelatedDomain(projectCode, businessTypeId, id);
      toast.success("数据域已解除关联");
      await queryClient.invalidateQueries({ queryKey: relationKey });
    } catch { toast.warning("解除关联失败，请刷新后重试"); }
  };
  const loadAll = async () => {
    setSaving(true);
    try {
      await businessTypeApi.loadAllDomains(projectCode, businessTypeId);
      toast.success("已加载全部数据域");
      await queryClient.invalidateQueries({ queryKey: relationKey });
    } catch { toast.warning("加载全部数据域失败，请重试"); }
    finally { setSaving(false); }
  };
  const columns: TableColumns<DataDomainRecord> = [
    { key: "cnName", title: "中文名", dataIndex: "cnName", width: 180 },
    { key: "enName", title: "英文名", dataIndex: "enName", width: 180 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 140 },
    { key: "remark", title: "备注" },
    { key: "actions", title: "操作", width: 88, align: "center", render: (_v, record) => (
      <Button size="sm" variant="ghost" className="text-xs text-[var(--yak-components-muted-text)] hover:text-[#d92d20]"
        onClick={() => record.id && void removeDomain(record.id)}>删除</Button>
    )},
  ];
  return (
    <section className={CARD}>
      <h3 className={TITLE}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />关联数据域</h3>
      <div className="mb-2 text-xs text-[var(--yak-components-muted-text)]">可选数据域：</div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Popover open={pickerOpen} onOpenChange={(open) => {
          setPickerOpen(open);
          if (open) setSelectedIds([]);
        }}>
          <PopoverTrigger className="inline-flex h-7 min-w-[260px] cursor-pointer items-center justify-between rounded-md border border-[var(--yak-components-input-border)] bg-white px-2.5 text-xs text-[var(--yak-components-muted-text)] hover:border-[var(--yak-color-primary)]">
            请选择数据域<ChevronDown size={13} />
          </PopoverTrigger>
          <PopoverContent align="start" className="w-[300px] p-0">
            <div className="max-h-64 overflow-y-auto p-3">
              {domainsQuery.isPending ? (
                <div className="py-4 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载数据域...</div>
              ) : (domainsQuery.data || []).filter((domain) => domain.id && !relatedIds.has(domain.id)).length === 0 ? (
                <div className="py-4 text-center text-xs text-[var(--yak-components-muted-text)]">暂无可关联的数据域</div>
              ) : (
                <div className="space-y-2">
                  {(domainsQuery.data || []).filter((domain) => domain.id && !relatedIds.has(domain.id)).map((domain) => {
                    const id = domain.id || "";
                    return (
                      <label key={id} className="flex cursor-pointer items-center gap-2 text-xs">
                        <Checkbox checked={selectedIds.includes(id)} onCheckedChange={(checked) => setSelectedIds((current) =>
                          checked ? Array.from(new Set([...current, id])) : current.filter((item) => item !== id),
                        )} />
                        <span>{domain.cnName} ({domain.enShortName || domain.enName || "-"})</span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
            <div className="flex items-center justify-between border-t border-[var(--yak-components-panel-border)] px-3 py-2">
              <span className="text-[11px] text-[var(--yak-components-muted-text)]">已选 {selectedIds.length} 项</span>
              <Button size="sm" variant="primary" isPending={saving} onClick={() => void addSelected()}>确定</Button>
            </div>
          </PopoverContent>
        </Popover>
        <Button size="sm" variant="ghost" className="text-xs text-[var(--yak-color-primary)]"
          isDisabled={saving} onClick={() => void loadAll()}>一键加载全部数据域</Button>
      </div>
      <div className="mb-2 text-xs text-[var(--yak-components-muted-text)]">已选数据域：</div>
      <Table<DataDomainRecord> columns={columns} dataSource={related} rowKey={(record) => record.id || JSON.stringify(record)}
        loading={relatedQuery.isPending || relatedQuery.isFetching} bordered size="small" emptyText="暂无关联数据域" />
    </section>
  );
}

export default BusinessTypeDomainRelation;
