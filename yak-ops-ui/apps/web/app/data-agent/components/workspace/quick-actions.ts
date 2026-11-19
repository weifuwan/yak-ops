import {
  Activity,
  Code2,
  Database,
  Workflow,
  type LucideIcon,
} from "lucide-react";

export const QUICK_ACTIONS: {
  id: string;
  label: string;
  prompt: string;
  Icon: LucideIcon;
  iconClass: string;
}[] = [
  {
    id: "table",
    label: "分析数据表",
    prompt: "请帮我分析这张数据表的结构、字段含义和潜在问题。",
    Icon: Database,
    iconClass: "bg-[#fff0ed] text-[#df795c]",
  },
  {
    id: "sql",
    label: "生成 SQL",
    prompt: "请根据我的需求生成 SQL，并解释查询逻辑和关键字段。",
    Icon: Code2,
    iconClass: "bg-[#eaf2ff] text-[#5685c8]",
  },
  {
    id: "task",
    label: "排查任务异常",
    prompt: "请帮我分析任务运行日志，定位失败原因并给出修复建议。",
    Icon: Activity,
    iconClass: "bg-[#fff5df] text-[#c58a35]",
  },
  {
    id: "workflow",
    label: "梳理数据开发流程",
    prompt: "请根据我的业务需求，帮我梳理数据集成与工作流开发方案。",
    Icon: Workflow,
    iconClass: "bg-[#eaf6ef] text-[#4f9a70]",
  },
];
