import { ModelingSectionPage } from "../components/modeling-section-page";

export default function DimensionModelingPage() {
  return (
    <ModelingSectionPage
      title="维度建模"
      description="围绕维度、维度表和数仓模型，逐步迁移原 data_modal 的维度建模能力。"
      capabilities={["维度管理", "维度表设计", "模型字段", "版本管理"]}
    />
  );
}
