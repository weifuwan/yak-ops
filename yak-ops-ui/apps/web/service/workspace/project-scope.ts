let activeProjectCode: string | undefined;

export function setActiveProjectCode(projectCode?: string): void {
  activeProjectCode = projectCode;
}

export function requireActiveProjectCode(): string {
  if (!activeProjectCode) {
    throw new Error("请先选择工作空间");
  }
  return activeProjectCode;
}
