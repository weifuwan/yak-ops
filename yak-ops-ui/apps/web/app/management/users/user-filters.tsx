import { Button, Input } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

interface Props {
  searchVal: string;
  canCreate: boolean;
  onSearchChange: (value: string) => void;
  onCreate: () => void;
}

export function UserFilters({ searchVal, canCreate, onSearchChange, onCreate }: Props) {
  return (
    <section className="flex shrink-0 flex-wrap items-center gap-2">
      {canCreate ? (
        <Button size="sm" variant="primary" onClick={onCreate}>
          <Plus size={14} />
          新增用户
        </Button>
      ) : null}
      <div className="relative w-[280px]">
        <Input
          size="small"
          variant="outlined"
          value={searchVal}
          placeholder="搜索用户名、邮箱或手机号"
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </div>
    </section>
  );
}
