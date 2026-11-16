import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";

import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { planningQueryKeys } from "../../query-keys";
import { DETAIL_CARD_CLASS, DescriptionItem, SectionTitle } from "../components/detail-fields";
import { getMartTypeLabel, type DataMartRecord } from "./types";
import type { SubjectDomainRecord } from "../subject-domain/types";

export function DataMartDetail({
  projectCode, record, onEdit, onAddChild, onEditChild, onDeleteChild,
  onEditSubjectDomain, onDeleteSubjectDomain,
}: {
  projectCode: string; record: DataMartRecord; onEdit: () => void; onAddChild: () => void;
  onEditChild: (record: DataMartRecord) => void; onDeleteChild: (record: DataMartRecord) => void;
  onEditSubjectDomain: (record: SubjectDomainRecord) => void;
  onDeleteSubjectDomain: (record: SubjectDomainRecord) => void;
}) {
  const [childPage, setChildPage] = useState(1);
  const [subjectPage, setSubjectPage] = useState(1);
  const pageSize = 10;
  const childrenQuery = useQuery({
    queryKey: planningQueryKeys.dataMartChildren(projectCode, record.id || "", childPage),
    queryFn: () => dataMartApi.page<DataMartRecord>(projectCode, {
      id: record.id, pageNo: childPage, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });
  const subjectQuery = useQuery({
    queryKey: planningQueryKeys.dataMartSubjectDomains(projectCode, record.id || "", subjectPage),
    queryFn: () => subjectDomainApi.page<SubjectDomainRecord>(projectCode, {
      dataMartId: record.id, pageNo: subjectPage, pageSize,
    }),
    enabled: Boolean(projectCode && record.id),
  });

  const childColumns: TableColumns<DataMartRecord> = [
    { key: "martName", title: "集市名称", dataIndex: "martName", width: 210 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150 },
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
  const subjectColumns: TableColumns<SubjectDomainRecord> = [
    { key: "domainName", title: "主题域名称", dataIndex: "domainName", width: 210 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 150 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    {
      key: "actions", title: "操作", width: 112, align: "center",
      render: (_value, item) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEditSubjectDomain(item)}>编辑</Button>
          <span className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDeleteSubjectDomain(item)}>删除</Button>
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
          <DescriptionItem label="集市名称" value={record.martName} />
          <DescriptionItem label="所属业务分类" value={record.businessTypeName} />
          <DescriptionItem label="集市类型" value={getMartTypeLabel(record)} />
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
          <SectionTitle>子数据集市</SectionTitle>
          <Button size="sm" variant="primary" onClick={onAddChild}><Plus size={13} />新建子数据集市</Button>
        </div>
        <Table<DataMartRecord>
          columns={childColumns} dataSource={childrenQuery.data?.totalList || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={childrenQuery.isPending || childrenQuery.isFetching}
          bordered size="small" emptyText="暂无子数据集市"
          pagination={(childrenQuery.data?.total || 0) > 0 ? {
            current: childPage, pageSize, total: childrenQuery.data?.total || 0,
            disabled: childrenQuery.isFetching, onChange: setChildPage,
          } : false}
        />
      </section>

      <section className={DETAIL_CARD_CLASS}>
        <div className="mb-3 flex items-center justify-between">
          <SectionTitle>主题域管理</SectionTitle>
        </div>
        <Table<SubjectDomainRecord>
          columns={subjectColumns} dataSource={subjectQuery.data?.totalList || []}
          rowKey={(item) => item.id || JSON.stringify(item)}
          loading={subjectQuery.isPending || subjectQuery.isFetching}
          bordered size="small" emptyText="暂无关联主题域"
          pagination={(subjectQuery.data?.total || 0) > 0 ? {
            current: subjectPage, pageSize, total: subjectQuery.data?.total || 0,
            disabled: subjectQuery.isFetching, onChange: setSubjectPage,
          } : false}
        />
      </section>
    </div>
  );
}

export default DataMartDetail;
