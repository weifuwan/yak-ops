import type { ResolvedTheme, ThemeMode } from "@/types/theme";

export const isThemeMode = (value: string | null): value is ThemeMode =>
  value === "light" || value === "dark" || value === "system";

export const resolveTheme = (
  theme: ThemeMode,
  prefersDark: boolean,
): ResolvedTheme => {
  if (theme === "system") return prefersDark ? "dark" : "light";
  return theme;
};
