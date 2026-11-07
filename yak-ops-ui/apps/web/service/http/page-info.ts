/** Shared API pagination contract from io.yak.ops.api.utils.PageInfo. */
export interface PageInfo<T> {
  totalList: T[];
  total: number;
  totalPage: number;
  pageSize: number;
  currentPage: number;
  /** Backend SQL offset; currentPage is the display page number. */
  pageNo: number;
}
