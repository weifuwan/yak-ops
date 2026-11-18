import { LoadingIndicator } from "@/app/components/loading-indicator";

export default function AppLoading() {
  return (
    <div
      role="status"
      aria-label="正在加载"
      className="flex h-dvh w-full items-center justify-center bg-white"
    >
      <LoadingIndicator />
      <span className="sr-only">正在加载</span>
    </div>
  );
}
