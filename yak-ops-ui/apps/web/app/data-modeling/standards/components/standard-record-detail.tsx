import { Table, type TableColumns } from "@yak-ops/yak-ui";

import type { CodeDetailRecord, CodeStandardRecord, FieldStandardRecord, StandardModule, StandardRecord } from "../types";
import { DescriptionItem, SectionTitle, STANDARD_DETAIL_CARD_CLASS } from "./detail-fields";

const PAGE_SIZE = 10;

function CodeDetailsTable({ items }: { items: CodeDetailRecord[] }) {
  const columns: TableColumns<CodeDetailRecord> = [
    { key: "enShortName", title: "编码取值", dataIndex: "enShortName", width: 180 },
    { key: "cnName", title: "中文名称", dataIndex: "cnName", width: 180 },
    { key: "enName", title: "英文名称", dataIndex: "enName", width: 220 },
    { key: "remark", title: "编码含义", dataIndex: "remark" },
  ];
  return (
    <section className={STANDARD_DETAIL_CARD_CLASS}>
      <div className="mb-3 flex items-center justify-between">
        <SectionTitle>代码明细</SectionTitle>
        <span className="text-xs text-[var(--yak-components-muted-text)]">{items.length} 条</span>
      </div>
      <Table<CodeDetailRecord>
        columns={columns} dataSource={items} rowKey={(item) => item.id || item.enShortName || ""}
        bordered size="small" emptyText="暂无代码明细" pagination={items.length > PAGE_SIZE ? { pageSize: PAGE_SIZE } : false}
      />
    </section>
  );
}

export function StandardRecordDetails({
  module, record, directoryName,
}: {
  module: StandardModule; record: StandardRecord; directoryName?: string;
}) {
  const field = record as FieldStandardRecord;
  const code = record as CodeStandardRecord;
  return (
    <>
      <section className={STANDARD_DETAIL_CARD_CLASS}>
        <div className="mb-3 flex items-center justify-between"><SectionTitle>基本信息</SectionTitle></div>
        <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-2 sm:grid-cols-2 xl:grid-cols-3">
          {module === "FIELD" ? (
            <>
              <DescriptionItem label="标准编码" value={field.stdCode} />
              <DescriptionItem label="中文名称" value={field.cnName} />
              <DescriptionItem label="英文缩写" value={field.enShortName} />
              <DescriptionItem label="英文名称" value={field.enName} />
              <DescriptionItem label="数据类型" value={field.fieldDataType} />
              <DescriptionItem label="字段长度" value={field.fieldLength} />
              <DescriptionItem label="字段精度" value={field.fieldPrecision} />
              <DescriptionItem label="允许为空" value={field.fieldCanNull} />
              <DescriptionItem label="默认值" value={field.fieldDefaultValue} />
              <DescriptionItem label="父级标准" value={field.parentStd} />
              <DescriptionItem label="引用代码" value={field.reference} />
              <DescriptionItem label="所属目录" value={directoryName} />
              <DescriptionItem label="业务定义" value={field.businessDefinition} className="col-span-full" multiline />
            </>
          ) : (
            <>
              <DescriptionItem label="英文缩写" value={record.enShortName} />
              <DescriptionItem label="英文名称" value={record.enName} />
              <DescriptionItem label="中文名称" value={record.cnName} />
              {module === "CODE" ? <DescriptionItem label="描述" value={code.remark} className="col-span-full" multiline /> : null}
              <DescriptionItem label="所属目录" value={directoryName} />
            </>
          )}
          <DescriptionItem label="创建人" value={record.createBy} />
          <DescriptionItem label="创建时间" value={record.createTime} />
          <DescriptionItem label="修改人" value={record.updateBy} />
          <DescriptionItem label="修改时间" value={record.updateTime} />
        </div>
      </section>
      {module === "CODE" ? <CodeDetailsTable items={code.detailList || []} /> : null}
    </>
  );
}

export default StandardRecordDetails;
