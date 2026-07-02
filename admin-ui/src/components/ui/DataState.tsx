import type { ReactNode } from "react";
import { Button, Skeleton, Empty } from "antd";
import { useTranslation } from "react-i18next";
import { selectDataState } from "./dataStateSelect";

interface Props {
  isLoading: boolean; isError: boolean; count: number;
  onRetry?: () => void; emptyAction?: ReactNode; children: ReactNode;
}
export function DataState({ isLoading, isError, count, onRetry, emptyAction, children }: Props) {
  const { t } = useTranslation();
  const kind = selectDataState({ isLoading, isError, count });
  if (kind === "loading") return <Skeleton active paragraph={{ rows: 6 }} />;
  if (kind === "error")
    return (
      <div style={{ textAlign: "center", padding: 48, color: "var(--muted)" }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>{t("errorTitle")}</div>
        {onRetry && <Button onClick={onRetry}>{t("retry")}</Button>}
      </div>
    );
  if (kind === "empty")
    return <Empty description={t("empty")} style={{ padding: 48 }}>{emptyAction}</Empty>;
  return <>{children}</>;
}
