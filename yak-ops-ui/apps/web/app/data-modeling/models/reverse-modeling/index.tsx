import { ModelingSectionPage } from "../components/modeling-section-page";

export default function ReverseModelingPage() {
  return (
    <ModelingSectionPage
      title="逆向建模"
      description="逆向建模能力正在规划中。当前可先进入维度建模，通过来源表字段导入完成模型初始化。"
      capabilities={["规划中：数据源选择", "规划中：表结构读取", "规划中：模型反向生成", "规划中：模型校验"]}
    />
  );
}
