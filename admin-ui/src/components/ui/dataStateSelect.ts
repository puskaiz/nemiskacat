export type DataStateKind = "loading" | "error" | "empty" | "data";
export function selectDataState(q: { isLoading: boolean; isError: boolean; count: number }): DataStateKind {
  if (q.isLoading) return "loading";
  if (q.isError) return "error";
  return q.count === 0 ? "empty" : "data";
}
