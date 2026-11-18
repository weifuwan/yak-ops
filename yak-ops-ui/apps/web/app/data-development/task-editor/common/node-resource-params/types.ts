export type NodeLocalParam = {
  prop: string;
  direct: "IN" | "OUT";
  type: string;
  value: string;
  sensitive: boolean;
  [key: string]: unknown;
};
