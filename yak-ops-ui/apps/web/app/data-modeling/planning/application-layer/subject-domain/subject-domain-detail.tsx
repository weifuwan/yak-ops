import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";

import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { planningQueryKeys } from "../../query-keys";
import { DETAIL_CARD_CLASS, DescriptionItem, SectionTitle } from "../components/detail-fields";
import type { SubjectDomainRecord } from "./types";

export function SubjectDomainDetail({
  projectCode, record, onEdit, onAddChild, onEditChild, onDeleteChild,
}: {
  projectCode: string; record: SubjectDomainRecord; onEdit: () => void; onAddChild: () => void;
  onEditChild: (record: SubjectDomainRecord) => void; onDeleteChild: (record: SubjectDomainRecord) => void;
}) {
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;
  const childrenQuery = useQuery({
    queryKey: planningQueryKeys.subjectDomainChildren(projectCode, record.id || "", pageNo),
    queryFn: () => subjectDomainApi.page<SubjectDomainRecord>(projectCode, {
      id: record.id, dataMartId: record.dataMartId, pageNo, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });
  useEffect(() => setPageNo(1), [record.id]);

  const columns: TableColumns<SubjectDomainRecord> = [
    { key: "domainName", title: "主题域名称", dataIndex: "domainName", width: 220 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 160 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEditChild(item)}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDeleteChild(item)}>删除</Button>
        </div>
      ),
    },
  ];

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-[#f2f3f5] p-4">
      <section className="bg-white pb-2">
        <div className="flex items-center justify-between p-4">
          <SectionTitle>基本信息</SectionTitle>
          <Button size="sm" variant="ghost" className="h-6 min-w-8 px-0 text-xs text-[#1677ff] hover:bg-transparent" onClick={onEdit}>编辑</Button>
        </div>
        <div className="mb-1 grid grid-cols-1 gap-x-8 gap-y-2 px-6 pb-2 sm:grid-cols-2 xl:grid-cols-4">
          <DescriptionItem label="英文缩写" value={record.enShortName} />
          <DescriptionItem label="主题域名称" value={record.domainName} />
          <DescriptionItem label="所属数据集市" value={record.dataMartName} className="xl:col-span-2" />
          <DescriptionItem label="负责人" value={record.director} />
          <DescriptionItem label="创建人" value={record.createBy} />
          <DescriptionItem label="创建时间" value={record.createTime} />
          <DescriptionItem label="修改人" value={record.updateBy} />
          <DescriptionItem label="修改时间" value={record.updateTime} />
          <DescriptionItem label="备注" value={record.remark} className="col-span-full" multiline />
        </div>
      </section>

      <section className={DETAIL_CARD_CLASS + " mt-4"}>
        <div className="mb-3 flex items-center justify-between">
          <SectionTitle>子主题域</SectionTitle>
          <Button size="sm" variant="primary" onClick={onAddChild}><Plus size={13} />新建子主题域</Button>
        </div>
        <Table<SubjectDomainRecord>
          columns={columns} dataSource={childrenQuery.data?.totalList || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={childrenQuery.isPending || childrenQuery.isFetching}
          bordered size="small" emptyText="暂无子主题域"
          pagination={(childrenQuery.data?.total || 0) > 0 ? {
            current: pageNo, pageSize, total: childrenQuery.data?.total || 0,
            disabled: childrenQuery.isFetching, onChange: setPageNo,
          } : false}
        />
      </section>
    </div>
  );
}

export default SubjectDomainDetail;
