import { useState } from "react";
import {
  Alert,
  Button,
  Checkbox,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import type {
  DataSyncPartitionAssignment,
  DataSyncTargetColumn,
  DataSyncTargetPartition,
  DataSyncTargetTableStructure,
} from "@/service/data-sync";
import { ContentField } from "./content-layout";

interface Props {
  value: DataSyncTargetTableStructure;
  sourceColumns?: Array<{ name: string; type: string }>;
  supportsPartitions: boolean;
  supportsTableTypes: boolean;
  onClose: () => void;
  onApply: (structure: DataSyncTargetTableStructure) => void;
}

const TABLE_TYPE_LABELS = {
  NORMAL: "普通表",
  PK_DELTA: "PK Delta Table",
  APPEND_DELTA: "Append Delta Table",
};

const PARTITION_ASSIGNMENT_TYPE_LABELS = {
  CONSTANT: "常量值",
  SOURCE_FIELD: "来源字段",
  EVENT_TIME: "事件时间",
};

const TIME_FORMAT_LABELS = {
  TIME_STRING: "时间字符串",
  TIME_OBJECT: "时间对象",
  UNIX_TIMESTAMP_SECONDS: "Unix 时间戳(秒)",
  UNIX_TIMESTAMP_MILLIS: "Unix 时间戳(毫秒)",
};

export function TargetStructureEditorDialog({
  value,
  sourceColumns,
  supportsPartitions,
  supportsTableTypes,
  onClose,
  onApply,
}: Props) {
  const [structure, setStructure] = useState<DataSyncTargetTableStructure>(() =>
    structuredClone(value),
  );
  const [error, setError] = useState("");
  const [editingPartition, setEditingPartition] = useState<{
    index: number;
    partition: DataSyncTargetPartition;
  }>();

  const apply = () => {
    try {
      if (structure.columns.length === 0) throw new Error("至少需要一个字段");
      if (structure.columns.some((col) => !col.name.trim())) throw new Error("字段名不能为空");
      if (structure.columns.some((col) => !col.type.trim())) throw new Error("字段类型不能为空");
      if (new Set(structure.columns.map((col) => col.name)).size !== structure.columns.length) {
        throw new Error("字段名不能重复");
      }
      if (structure.partitions) {
        if (structure.partitions.some((p) => !p.name.trim())) throw new Error("分区字段名不能为空");
        if (new Set(structure.partitions.map((p) => p.name)).size !== structure.partitions.length) {
          throw new Error("分区字段名不能重复");
        }
      }
      onApply(structure);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "表结构配置无效");
    }
  };

  const addColumn = () => {
    setStructure({
      ...structure,
      columns: [...structure.columns, { name: "", type: "STRING" }],
    });
  };

  const updateColumn = (index: number, updates: Partial<DataSyncTargetColumn>) => {
    const columns = [...structure.columns];
    columns[index] = { ...columns[index], ...updates };
    setStructure({ ...structure, columns });
  };

  const removeColumn = (index: number) => {
    setStructure({
      ...structure,
      columns: structure.columns.filter((_, i) => i !== index),
    });
  };

  const addPartition = () => {
    setEditingPartition({
      index: structure.partitions?.length || 0,
      partition: {
        name: "",
        assignment: { type: "CONSTANT", value: "" },
      },
    });
  };

  const savePartition = () => {
    if (!editingPartition) return;
    const partitions = [...(structure.partitions || [])];
    if (editingPartition.index >= partitions.length) {
      partitions.push(editingPartition.partition);
    } else {
      partitions[editingPartition.index] = editingPartition.partition;
    }
    setStructure({ ...structure, partitions });
    setEditingPartition(undefined);
  };

  const removePartition = (index: number) => {
    setStructure({
      ...structure,
      partitions: structure.partitions?.filter((_, i) => i !== index),
    });
  };

  const columnColumns: TableColumns<DataSyncTargetColumn & { index: number }> = [
    { key: "index", title: "序号", width: 60, render: (_, row) => row.index + 1 },
    {
      key: "name",
      title: "字段名",
      width: 200,
      render: (_, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.name}
          onChange={(e) => updateColumn(row.index, { name: e.target.value })}
        />
      ),
    },
    {
      key: "type",
      title: "字段类型",
      width: 180,
      render: (_, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.type}
          onChange={(e) => updateColumn(row.index, { type: e.target.value })}
        />
      ),
    },
    {
      key: "nullable",
      title: "可为空",
      width: 80,
      render: (_, row) => (
        <Checkbox
          checked={row.nullable !== false}
          onCheckedChange={(checked) => updateColumn(row.index, { nullable: checked as boolean })}
        />
      ),
    },
    {
      key: "defaultValue",
      title: "默认值",
      width: 150,
      render: (_, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.defaultValue || ""}
          placeholder="可选"
          onChange={(e) => updateColumn(row.index, { defaultValue: e.target.value || undefined })}
        />
      ),
    },
    {
      key: "comment",
      title: "注释",
      width: 200,
      render: (_, row) => (
        <Input
          size="small"
          variant="outlined"
          value={row.comment || ""}
          placeholder="可选"
          onChange={(e) => updateColumn(row.index, { comment: e.target.value || undefined })}
        />
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 80,
      fixed: "right",
      render: (_, row) => (
        <Button size="small" variant="ghost" onClick={() => removeColumn(row.index)}>
          删除
        </Button>
      ),
    },
  ];

  const renderPartitionAssignment = (assignment: DataSyncPartitionAssignment) => {
    if (assignment.type === "CONSTANT") return `常量: ${assignment.value}`;
    if (assignment.type === "SOURCE_FIELD" && assignment.valueType === "ENUM")
      return `来源字段: ${assignment.field} (枚举值)`;
    if (assignment.type === "SOURCE_FIELD" && assignment.valueType === "TIMESTAMP")
      return `来源字段: ${assignment.field} (${TIME_FORMAT_LABELS[assignment.sourceFormat]}) → ${assignment.targetFormat}`;
    if (assignment.type === "EVENT_TIME")
      return `事件时间 (${TIME_FORMAT_LABELS[assignment.sourceFormat]}) → ${assignment.targetFormat}`;
    return "未配置";
  };

  return (
    <Modal
      open
      title="编辑目标表结构"
      width={1200}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {supportsTableTypes && (
          <ContentField label="表类型">
            <Select
              value={structure.tableType || "NORMAL"}
              onValueChange={(value) =>
                setStructure({
                  ...structure,
                  tableType: value as DataSyncTargetTableStructure["tableType"],
                })
              }
            >
              <SelectTrigger size="small" className="w-64">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(TABLE_TYPE_LABELS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    {label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </ContentField>
        )}

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label className="text-sm font-medium">字段列表</label>
            <div className="flex gap-2">
              {sourceColumns && (
                <Button
                  size="small"
                  variant="ghost"
                  onClick={() =>
                    setStructure({
                      ...structure,
                      columns: sourceColumns.map((col) => ({ ...col, nullable: true })),
                    })
                  }
                >
                  从源表导入
                </Button>
              )}
              <Button size="small" onClick={addColumn}>
                新增字段
              </Button>
            </div>
          </div>
          <Table
            columns={columnColumns}
            dataSource={structure.columns.map((col, index) => ({ ...col, index }))}
            rowKey="index"
            size="small"
            rowHeight={40}
            scroll={{ y: 300 }}
            pagination={false}
            emptyText="暂无字段"
          />
        </div>

        {supportsPartitions && (
          <div>
            <div className="mb-2 flex items-center justify-between">
              <label className="text-sm font-medium">分区字段</label>
              <Button size="small" onClick={addPartition}>
                新增分区字段
              </Button>
            </div>
            {structure.partitions && structure.partitions.length > 0 ? (
              <div className="space-y-2">
                {structure.partitions.map((partition, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between rounded border border-[var(--yak-border)] p-3"
                  >
                    <div>
                      <div className="font-medium">{partition.name}</div>
                      <div className="text-xs text-[var(--yak-components-muted-text)]">
                        {renderPartitionAssignment(partition.assignment)}
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        size="small"
                        variant="ghost"
                        onClick={() => setEditingPartition({ index, partition: { ...partition } })}
                      >
                        编辑
                      </Button>
                      <Button size="small" variant="ghost" onClick={() => removePartition(index)}>
                        删除
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="rounded border border-dashed border-[var(--yak-border)] p-4 text-center text-sm text-[var(--yak-components-muted-text)]">
                暂无分区字段
              </div>
            )}
          </div>
        )}

        {error && <Alert>{error}</Alert>}
      </div>

      {editingPartition && (
        <PartitionAssignmentDialog
          partition={editingPartition.partition}
          sourceColumns={sourceColumns?.map((c) => c.name) || []}
          onClose={() => setEditingPartition(undefined)}
          onApply={(partition) => {
            setEditingPartition({ ...editingPartition, partition });
            savePartition();
          }}
        />
      )}
    </Modal>
  );
}

interface PartitionAssignmentDialogProps {
  partition: DataSyncTargetPartition;
  sourceColumns: string[];
  onClose: () => void;
  onApply: (partition: DataSyncTargetPartition) => void;
}

function PartitionAssignmentDialog({
  partition: initialPartition,
  sourceColumns,
  onClose,
  onApply,
}: PartitionAssignmentDialogProps) {
  const [partition, setPartition] = useState<DataSyncTargetPartition>(() =>
    structuredClone(initialPartition),
  );
  const [error, setError] = useState("");

  const apply = () => {
    try {
      if (!partition.name.trim()) throw new Error("分区字段名不能为空");
      const { assignment } = partition;
      if (assignment.type === "CONSTANT" && !assignment.value.trim()) {
        throw new Error("常量值不能为空");
      }
      if (assignment.type === "SOURCE_FIELD" && !assignment.field.trim()) {
        throw new Error("来源字段不能为空");
      }
      if (
        assignment.type === "SOURCE_FIELD" &&
        assignment.valueType === "TIMESTAMP" &&
        !assignment.targetFormat.trim()
      ) {
        throw new Error("目标格式不能为空");
      }
      if (assignment.type === "EVENT_TIME" && !assignment.targetFormat.trim()) {
        throw new Error("目标格式不能为空");
      }
      onApply(partition);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "分区配置无效");
    }
  };

  const updateAssignment = (updates: Partial<DataSyncPartitionAssignment>) => {
    setPartition({
      ...partition,
      assignment: { ...partition.assignment, ...updates } as DataSyncPartitionAssignment,
    });
  };

  return (
    <Modal
      open
      title="配置分区字段赋值"
      width={700}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <ContentField label="分区字段名" required>
          <Input
            size="small"
            variant="outlined"
            value={partition.name}
            onChange={(e) => setPartition({ ...partition, name: e.target.value })}
          />
        </ContentField>

        <ContentField label="赋值方式" required>
          <Select
            value={partition.assignment.type}
            onValueChange={(value) => {
              if (value === "CONSTANT") {
                updateAssignment({ type: "CONSTANT", value: "" });
              } else if (value === "SOURCE_FIELD") {
                updateAssignment({
                  type: "SOURCE_FIELD",
                  field: "",
                  valueType: "ENUM",
                } as DataSyncPartitionAssignment);
              } else if (value === "EVENT_TIME") {
                updateAssignment({
                  type: "EVENT_TIME",
                  sourceFormat: "TIME_OBJECT",
                  targetFormat: "yyyyMMdd",
                } as DataSyncPartitionAssignment);
              }
            }}
          >
            <SelectTrigger size="small">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(PARTITION_ASSIGNMENT_TYPE_LABELS).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </ContentField>

        {partition.assignment.type === "CONSTANT" && (
          <ContentField label="常量值" required>
            <Input
              size="small"
              variant="outlined"
              value={partition.assignment.value}
              onChange={(e) => updateAssignment({ value: e.target.value })}
            />
          </ContentField>
        )}

        {partition.assignment.type === "SOURCE_FIELD" && (
          <>
            <ContentField label="来源字段" required>
              <Select
                value={partition.assignment.field || ""}
                onValueChange={(value) => updateAssignment({ field: value ?? "" })}
              >
                <SelectTrigger size="small">
                  <SelectValue placeholder="选择来源字段" />
                </SelectTrigger>
                <SelectContent>
                  {sourceColumns.map((col) => (
                    <SelectItem key={col} value={col}>
                      {col}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </ContentField>

            <ContentField label="取值类型">
              <Select
                value={partition.assignment.valueType}
                onValueChange={(value) =>
                  updateAssignment({
                    valueType: value as "ENUM" | "TIMESTAMP",
                    ...(value === "TIMESTAMP"
                      ? { sourceFormat: "TIME_OBJECT", targetFormat: "yyyyMMdd" }
                      : {}),
                  } as Partial<DataSyncPartitionAssignment>)
                }
              >
                <SelectTrigger size="small">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ENUM">字段值</SelectItem>
                  <SelectItem value="TIMESTAMP">时间值</SelectItem>
                </SelectContent>
              </Select>
            </ContentField>

            {partition.assignment.valueType === "TIMESTAMP" && (
              <>
                <ContentField label="源端格式">
                  <Select
                    value={partition.assignment.sourceFormat}
                    onValueChange={(value) => updateAssignment({ sourceFormat: value as any })}
                  >
                    <SelectTrigger size="small">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.entries(TIME_FORMAT_LABELS).map(([value, label]) => (
                        <SelectItem key={value} value={value}>
                          {label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </ContentField>

                {partition.assignment.sourceFormat === "TIME_STRING" && (
                  <ContentField label="源端时间格式" description="如: yyyy-MM-dd HH:mm:ss">
                    <Input
                      size="small"
                      variant="outlined"
                      value={partition.assignment.sourcePattern || ""}
                      placeholder="yyyy-MM-dd HH:mm:ss"
                      onChange={(e) => updateAssignment({ sourcePattern: e.target.value })}
                    />
                  </ContentField>
                )}

                <ContentField
                  label="目标格式"
                  description="分区命名格式，如: yyyyMMdd, yyyyMM"
                  required
                >
                  <Input
                    size="small"
                    variant="outlined"
                    value={partition.assignment.targetFormat}
                    placeholder="yyyyMMdd"
                    onChange={(e) => updateAssignment({ targetFormat: e.target.value })}
                  />
                </ContentField>
              </>
            )}
          </>
        )}

        {partition.assignment.type === "EVENT_TIME" && (
          <>
            <ContentField label="源端格式">
              <Select
                value={partition.assignment.sourceFormat}
                onValueChange={(value) => updateAssignment({ sourceFormat: value as any })}
              >
                <SelectTrigger size="small">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(TIME_FORMAT_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </ContentField>

            <ContentField
              label="目标格式"
              description="分区命名格式，如: yyyyMMdd, yyyyMM"
              required
            >
              <Input
                size="small"
                variant="outlined"
                value={partition.assignment.targetFormat}
                placeholder="yyyyMMdd"
                onChange={(e) => updateAssignment({ targetFormat: e.target.value })}
              />
            </ContentField>
          </>
        )}

        <Alert>
          <div className="text-xs">
            <div className="mb-1 font-medium">注意事项:</div>
            <ul className="list-inside list-disc space-y-1">
              <li>建议使用有限枚举值的字段作为分区，避免分区数过多</li>
              <li>单日新增分区超过 1000 个将导致任务失败</li>
              <li>谨慎使用秒级、毫秒级的分区创建方式</li>
            </ul>
          </div>
        </Alert>

        {error && <Alert>{error}</Alert>}
      </div>
    </Modal>
  );
}
