import { Input } from "@yak-ops/yak-ui";
import { Search } from "lucide-react";

type Props = {
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
};

export function IndicatorTreeSearch({ value, placeholder, onChange }: Props) {
  return (
    <div className="shrink-0 px-3 pb-2">
      <div className="relative">
        <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
        <Input size="small" variant="outlined" className="pl-8" value={value} placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)} />
      </div>
    </div>
  );
}

export default IndicatorTreeSearch;
