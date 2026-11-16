export const businessProcessQueryKeys = {
  detail: (projectCode: string, id: string) =>
    ["modeling", "business-process", "detail", projectCode, id] as const,
};
