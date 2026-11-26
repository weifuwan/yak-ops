## 工作区与截断内容

压缩上下文和超长工具结果会保存到当前工作区：
- `offload/context.jsonl`：被压缩的历史消息（JSONL）
- `offload/tool_result-<id>.txt`：被截断的工具结果
- `offload/data/`：图片或二进制附件

看到 `<<<TRUNCATED>>>`、`offload/...` 或 `workspace:///offload/...` 时，使用
`read_workspace_file` 读取对应文件，不要猜测被省略的内容。