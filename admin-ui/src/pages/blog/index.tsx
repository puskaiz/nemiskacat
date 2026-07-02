import { type CSSProperties } from "react";
import { useTable } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Tag } from "antd";
import { Pagination } from "../../components/ui/Pagination";

// Blog post list backed by GET /api/admin/blog/posts (X-Total-Count).
// Columns: title, slug, status badge (Piszkozat / Publikált), updatedAt.
// Row click → edit. "Új cikk" → create.

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

interface PostSummary {
  id: number;
  slug: string;
  title: string;
  status: string;
  updatedAt: string;
}

export function BlogList() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { tableQueryResult, current, setCurrent, pageSize } = useTable<PostSummary>({
    resource: "blog/posts",
    syncWithLocation: true,
    pagination: { pageSize: 10 },
  });

  const rows = tableQueryResult.data?.data ?? [];
  const total = tableQueryResult.data?.total ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("blog:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("blog:subtitle", { count: total })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <button type="button" style={accentPill} onClick={() => navigate("/blog/create")}>
          {t("blog:newPost")}
        </button>
      </div>

      <Table<PostSummary>
        dataSource={rows}
        rowKey="id"
        loading={tableQueryResult.isLoading}
        pagination={false}
        locale={{ emptyText: t("blog:empty") }}
        onRow={(r) => ({
          onClick: () => navigate(`/blog/edit/${r.id}`),
          style: { cursor: "pointer" },
        })}
      >
        <Table.Column title={t("blog:colTitle")} dataIndex="title" />
        <Table.Column title={t("blog:colSlug")} dataIndex="slug" />
        <Table.Column<PostSummary>
          title={t("blog:colStatus")}
          dataIndex="status"
          render={(status: string) =>
            status === "PUBLISHED" ? (
              <Tag color="green">{t("blog:statusPublished")}</Tag>
            ) : (
              <Tag>{t("blog:statusDraft")}</Tag>
            )
          }
        />
        <Table.Column<PostSummary>
          title={t("blog:colUpdatedAt")}
          dataIndex="updatedAt"
          render={(v: string) => new Date(v).toLocaleString("hu-HU")}
        />
      </Table>
      <Pagination current={current} pageSize={pageSize} total={total} onChange={setCurrent} />
    </div>
  );
}
