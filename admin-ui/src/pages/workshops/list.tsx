import { type CSSProperties } from "react";
import { useTable, useDelete } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { App, Button, Popconfirm, Table, Tag } from "antd";
import { DeleteOutlined } from "@ant-design/icons";
import type { Workshop } from "../../types";

// Real workshops list backed by GET /api/admin/workshops (X-Total-Count).
// Columns: name, slug, status tag, session count, image count, and a delete
// icon (with confirm; surfaces a 409 message if the workshop has bookings).
// Rows are clickable and open the workshop editor. Replaces the former static
// calendar mock.

const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer", border: 0 };

export const WorkshopList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { tableQueryResult } = useTable<Workshop>({
    resource: "workshops",
    syncWithLocation: true,
  });
  const { mutate: deleteWorkshop } = useDelete();

  const rows = tableQueryResult.data?.data ?? [];
  const total = tableQueryResult.data?.total ?? 0;

  const handleDelete = (id: number) => {
    deleteWorkshop(
      { resource: "workshops", id },
      {
        onSuccess: () => {
          message.success(t("workshops:deleted"));
          tableQueryResult.refetch();
        },
        onError: (error) => {
          const status = (error as { statusCode?: number }).statusCode;
          if (status === 409) {
            message.error(error.message || t("workshops:deleteHasBookings"));
          } else {
            message.error(error.message || t("workshops:deleteFailed"));
          }
        },
      },
    );
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div>
          <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>{t("workshops:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 2 }}>{t("workshops:subtitle", { count: total })}</div>
        </div>
        <div style={{ flex: 1 }} />
        <button type="button" style={accentPill} onClick={() => navigate("/workshops/create")}>
          {t("workshops:newWorkshop")}
        </button>
      </div>

      <Table<Workshop>
        dataSource={rows}
        rowKey="id"
        loading={tableQueryResult.isLoading}
        pagination={false}
        locale={{ emptyText: t("workshops:empty") }}
        onRow={(r) => ({
          onClick: () => navigate(`/workshops/edit/${r.id}`),
          style: { cursor: "pointer" },
        })}
      >
        <Table.Column title={t("workshops:colName")} dataIndex="name" />
        <Table.Column title={t("workshops:colSlug")} dataIndex="slug" />
        <Table.Column<Workshop>
          title={t("workshops:colStatus")}
          dataIndex="status"
          render={(status: Workshop["status"]) =>
            status === "PUBLISHED" ? (
              <Tag color="green">{t("workshops:statusPublished")}</Tag>
            ) : (
              <Tag>{t("workshops:statusDraft")}</Tag>
            )
          }
        />
        <Table.Column<Workshop>
          title={t("workshops:colSessions")}
          dataIndex="sessionCount"
          render={(v: number | undefined) => v ?? 0}
        />
        <Table.Column<Workshop>
          title={t("workshops:colImages")}
          render={(_, r) => r.images?.length ?? 0}
        />
        <Table.Column<Workshop>
          title={t("workshops:colActions")}
          render={(_, r) => (
            // stopPropagation so the delete control doesn't trigger the row's edit navigation
            <Popconfirm
              title={t("workshops:deleteConfirm")}
              description={t("workshops:deleteConfirmDesc")}
              okText={t("workshops:delete")}
              cancelText={t("workshops:cancel")}
              onConfirm={() => handleDelete(r.id)}
            >
              <Button
                danger
                size="small"
                type="text"
                icon={<DeleteOutlined />}
                aria-label={t("workshops:delete")}
                title={t("workshops:delete")}
                onClick={(e) => e.stopPropagation()}
              />
            </Popconfirm>
          )}
        />
      </Table>
    </div>
  );
};
