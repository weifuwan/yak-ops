import { useState } from "react";
import { Alert, Button, Modal, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { DataSyncCapabilities, DataSyncTypeMapping } from "@/service/data-sync";
import { validateTypeMappings } from "@/service/data-sync/table-configuration";
import { parameterFingerprint } from "@/service/data-sync/versions";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { DataSyncSearchableSelect } from "../components/searchable-select";

export function TypeMappingDialog({
  value,
  rules,
  onApply,
  onClose,
}: {
  value?: DataSyncTypeMapping[];
  rules: DataSyncCapabilities["typeMappingRules"];
  onApply: (value: DataSyncTypeMapping[]) => void;
  onClose: () => void;
}) {
  const [mappings, setMappings] = useState(() => structuredClone(value ?? []));
  const [error, setError] = useState("");
  useUnsavedChanges(parameterFingerprint(mappings) !== parameterFingerprint(value ?? []));
  const patch = (index: number, change: Partial<DataSyncTypeMapping>) =>
    setMappings(
      mappings.map((rule, position) => (position === index ? { ...rule, ...change } : rule)),
    );
  const columns: TableColumns<DataSyncTypeMapping & { index: number }> = [
    {
      key: "source",
      title: "来源类型",
      width: "34%",
      render: (_, row) => (
        <DataSyncSearchableSelect
          ariaLabel={`来源类型 ${row.index + 1}`}
          value={row.sourceType || null}
          placeholder="选择来源类型"
          options={rules.map((rule) => ({ value: rule.sourceType, label: rule.sourceType }))}
          onValueChange={(sourceType) => patch(row.index, { sourceType, targetType: "" })}
        />
      ),
    },
    {
      key: "target",
      title: "目标类型",
      width: "40%",
      render: (_, row) => (
        <DataSyncSearchableSelect
          ariaLabel={`目标类型 ${row.index + 1}`}
          value={row.targetType || null}
          placeholder="选择目标类型"
          options={(
            rules.find((rule) => rule.sourceType === row.sourceType)?.targetTypes ?? []
          ).map((targetType) => ({ value: targetType, label: targetType }))}
          onValueChange={(targetType) => patch(row.index, { targetType })}
        />
      ),
    },
    {
      key: "actions",
      title: "操作",
      render: (_, row) => (
        <Button
          size="small"
          variant="ghost"
          onClick={() => setMappings(mappings.filter((_, index) => index !== row.index))}
        >
          删除
        </Button>
      ),
    },
  ];
  return (
    <Modal
      open
      title="编辑字段类型映射"
      width={780}
      onClose={onClose}
      footer={
        <>
          <Button
            size="small"
            variant="primary"
            onClick={() => {
              try {
                validateTypeMappings(mappings);
                for (const mapping of mappings)
                  if (
                    !rules
                      .find((rule) => rule.sourceType === mapping.sourceType)
                      ?.targetTypes.includes(mapping.targetType)
                  )
                    throw new Error("所选字段类型不支持此转换");
                onApply(mappings);
              } catch (failure) {
                setError(failure instanceof Error ? failure.message : "类型映射无效");
              }
            }}
          >
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <Table
        columns={columns}
        dataSource={mappings.map((mapping, index) => ({ ...mapping, index }))}
        rowKey="index"
        size="small"
        pagination={false}
        emptyText="使用连接器默认类型映射"
      />
      <Button
        size="small"
        className="mt-3"
        onClick={() => setMappings([...mappings, { sourceType: "", targetType: "" }])}
      >
        添加类型映射
      </Button>
      {mappings.some(
        (mapping) => rules.find((rule) => rule.sourceType === mapping.sourceType)?.conditional,
      ) ? (
        <p className="mt-3 text-xs text-[var(--yak-components-muted-text)]">
          目标精度和长度不能小于实际来源列。
        </p>
      ) : null}
      {error ? <Alert className="mt-3">{error}</Alert> : null}
    </Modal>
  );
}
