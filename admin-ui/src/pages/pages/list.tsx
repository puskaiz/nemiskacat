import { type CSSProperties } from "react";
import { useTable } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Tag } from "antd";
import { Pagination } from "../../components/ui/Pagination";

const accentPill: CSSProperties = {
  borderRadius: 999,
  padding: "9px 18px",
  fontSize: 13,
  fontWeight: 600,
  color: "#fff",
  background: "var(--accent)",
  boxShadow: "0 2px 6px rgba(22,24,29,.3)",
  cursor: "pointer",
  border: 0,
};

interface PageSummary {
  id: number;
  slug: string;
  title: string;
  status: string;
  updatedAt: string;
}

export function PageList() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { tableQueryResult, current, setCurrent, pageSize } = useTable<PageSummary>({
    resource: "pages",
    syncWithLocation: true,
    pagination: { pageSize: 10 },
  });

  const rows = tableQueryResult.data?.data ?? [];
  const total = tableQueryResult.data?.total ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("pages:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("pages:subtitle", { count: total })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <button type="button" style={accentPill} onClick={() => navigate("/pages/create")}>
          {t("pages:newPage")}
        </button>
      </div>

      <Table<PageSummary>
        dataSource={rows}
        rowKey="id"
        loading={tableQueryResult.isLoading}
        pagination={false}
        locale={{ emptyText: t("pages:empty") }}
        onRow={(r) => ({
          onClick: () => navigate(`/pages/edit/${r.id}`),
          style: { cursor: "pointer" },
        })}
      >
        <Table.Column title={t("pages:colTitle")} dataIndex="title" />
        <Table.Column title={t("pages:colSlug")} dataIndex="slug" />
        <Table.Column<PageSummary>
          title={t("pages:colStatus")}
          dataIndex="status"
          render={(status: string) =>
            status === "PUBLISHED" ? (
              <Tag color="green">{t("pages:statusPublished")}</Tag>
            ) : (
              <Tag>{t("pages:statusDraft")}</Tag>
            )
          }
        />
        <Table.Column<PageSummary>
          title={t("pages:colUpdatedAt")}
          dataIndex="updatedAt"
          render={(v: string) => new Date(v).toLocaleString("hu-HU")}
        />
      </Table>
      <Pagination current={current} pageSize={pageSize} total={total} onChange={setCurrent} />
    </div>
  );
}
