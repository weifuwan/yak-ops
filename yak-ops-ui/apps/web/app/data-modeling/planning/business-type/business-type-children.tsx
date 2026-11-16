import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { planningQueryKeys } from "../query-keys";
import type { BusinessTypeRecord } from "./types";

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";

export function BusinessTypeChildren({
  projectCode, parent, onAdd, onEdit, onDelete,
}: {
  projectCode: string; parent: BusinessTypeRecord; onAdd: () => void;
  onEdit: (record: BusinessTypeRecord) => void; onDelete: (record: BusinessTypeRecord) => void;
}) {
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;
  const query = useQuery({
    queryKey: planningQueryKeys.businessTypeChildren(projectCode, parent.id || "", pageNo, pageSize),
    queryFn: () => businessTypeApi.page<BusinessTypeRecord>(projectCode, { id: parent.id, pageNo, pageSize }),
    enabled: Boolean(projectCode && parent.id),
  });
  useEffect(() => setPageNo(1), [parent.id]);
  const columns: TableColumns<BusinessTypeRecord> = [
    { key: "businessName", title: "业务名称", dataIndex: "businessName", width: 220 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 180 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    { key: "actions", title: "操作", width: 116, align: "center", render: (_v, record) => (
      <div className="flex items-center justify-center gap-1">
        <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEdit(record)}>编辑</Button>
        <span className="h-3 w-px bg-[#e4e7ec]" />
        <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDelete(record)}>删除</Button>
      </div>
    )},
  ];
  return (
    <section className={CARD}>
      <div className="mb-3 flex items-center justify-between">
        <h3 className={TITLE.replace("mb-4 ", "")}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />子业务分类</h3>
        <Button size="sm" variant="primary" onClick={onAdd}><Plus size={13} />新建子业务分类</Button>
      </div>
      <Table<BusinessTypeRecord> columns={columns} dataSource={query.data?.totalList || []}
        rowKey={(record) => record.id || JSON.stringify(record)} loading={query.isPending || query.isFetching}
        bordered size="small" emptyText="暂无子业务分类"
        pagination={(query.data?.total || 0) > 0 ? {
          current: pageNo, pageSize, total: query.data?.total || 0,
          disabled: query.isFetching, onChange: (page) => setPageNo(page),
        } : false} />
    </section>
  );
}

export default BusinessTypeChildren;
