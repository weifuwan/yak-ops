function pad(value: number): string {
  return String(value).padStart(2, "0");
}

export function formatScriptCreatedAt(date: Date): string {
  const datePart = [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join("-");
  const timePart = [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join(":");

  return datePart + " " + timePart;
}

export function buildScriptTaskCommentTemplate(
  createdBy: string,
  createdAt: Date = new Date(),
): string {
  const author = createdBy.trim() || "当前用户";
  const separator = "# " + "=".repeat(60);

  return [
    separator,
    "# 创建时间：" + formatScriptCreatedAt(createdAt),
    "# 创建人：" + author,
    "# 描述：请填写脚本用途",
    separator,
    "",
    "",
  ].join("\n");
}
