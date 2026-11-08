import { Button, Input } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

interface Props {
  userName: string;
  realName: string;
  onUserNameChange: (value: string) => void;
  onRealNameChange: (value: string) => void;
  onCreate: () => void;
}

export function UserFilters({
  userName,
  realName,
  onUserNameChange,
  onRealNameChange,
  onCreate,
}: Props) {
  return (
    <section className="flex shrink-0 flex-wrap items-center gap-2">
      <Button size="sm" variant="primary" onClick={onCreate}>
        <Plus size={14} />
        新增用户
      </Button>

      <div className="relative w-[260px]">
        <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]">
          用户名
        </span>
        <Input
          size="small"
          variant="outlined"
          value={userName}
          className="pl-[62px]"
          placeholder="请输入用户名"
          onChange={(event) => {
            onUserNameChange(event.target.value);
          }}
        />
      </div>

      <div className="relative w-[260px]">
        <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]">
          姓名
        </span>
        <Input
          size="small"
          variant="outlined"
          value={realName}
          className="pl-[50px]"
          placeholder="请输入姓名"
          onChange={(event) => {
            onRealNameChange(event.target.value);
          }}
        />
      </div>
    </section>
  );
}
