import { ModelingSectionPage } from "../components/modeling-section-page";

export default function ReverseModelingPage() {
  return (
    <ModelingSectionPage
      title="逆向建模"
      description="预留数据库逆向解析、表结构导入和模型生成入口，后续逐步迁移原有功能。"
      capabilities={["数据源选择", "表结构读取", "模型反向生成", "模型校验"]}
    />
  );
}
