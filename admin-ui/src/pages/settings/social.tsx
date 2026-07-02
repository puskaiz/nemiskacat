import { useCreate, useDelete, useTable, useUpdate } from "@refinedev/core";
import { Button, Input, Space, Table, message } from "antd";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { API_BASE, apiFetch } from "../../api/http";
import type { SocialLink } from "../../types";

export const SocialSettings = () => {
  const { t } = useTranslation();
  const { tableQueryResult } = useTable<SocialLink>({
    resource: "settings/social",
    pagination: { mode: "off" },
  });
  const rows = [...(tableQueryResult.data?.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);
  const refetch = () => tableQueryResult.refetch();

  const { mutate: create } = useCreate<SocialLink>();
  const { mutate: update } = useUpdate<SocialLink>();
  const { mutate: doDelete } = useDelete();

  // editing state: id -> { network, url }
  const [editState, setEditState] = useState<Record<number, { network: string; url: string }>>({});
  // new row state
  const [newRow, setNewRow] = useState<{ network: string; url: string } | null>(null);

  const startEdit = (row: SocialLink) => {
    setEditState((prev) => ({ ...prev, [row.id]: { network: row.network, url: row.url } }));
  };

  const cancelEdit = (id: number) => {
    setEditState((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  };

  const saveEdit = (id: number) => {
    const vals = editState[id];
    if (!vals) return;
    update(
      { resource: "settings/social", id, values: vals },
      {
        onSuccess: () => {
          message.success(t("social:saved"));
          cancelEdit(id);
          void refetch();
        },
        onError: () => message.error(t("social:saveFailed")),
      },
    );
  };

  const saveNew = () => {
    if (!newRow) return;
    create(
      { resource: "settings/social", values: newRow },
      {
        onSuccess: () => {
          message.success(t("social:saved"));
          setNewRow(null);
          void refetch();
        },
        onError: () => message.error(t("social:saveFailed")),
      },
    );
  };

  const remove = (id: number) => {
    doDelete(
      { resource: "settings/social", id },
      {
        onSuccess: () => void refetch(),
        onError: () => message.error(t("social:saveFailed")),
      },
    );
  };

  const move = async (index: number, dir: -1 | 1) => {
    const swap = index + dir;
    if (swap < 0 || swap >= rows.length) return;
    const ids = rows.map((r) => r.id);
    [ids[index], ids[swap]] = [ids[swap], ids[index]];
    const res = await apiFetch(`${API_BASE}/settings/social/reorder`, {
      method: "POST",
      body: JSON.stringify({ ids }),
    });
    if (res.ok) { await refetch(); } else { message.error(t("social:saveFailed")); }
  };

  return (
    <div>
      <h1>{t("social:title")}</h1>
      <p>{t("social:subtitle")}</p>
      <Table<SocialLink> dataSource={rows} rowKey="id" pagination={false}>
        <Table.Column<SocialLink>
          title={t("social:colNetwork")}
          dataIndex="network"
          render={(_, r) =>
            editState[r.id] !== undefined ? (
              <Input
                value={editState[r.id].network}
                onChange={(e) => setEditState((prev) => ({ ...prev, [r.id]: { ...prev[r.id], network: e.target.value } }))}
              />
            ) : (
              r.network
            )
          }
        />
        <Table.Column<SocialLink>
          title={t("social:colUrl")}
          dataIndex="url"
          render={(_, r) =>
            editState[r.id] !== undefined ? (
              <Input
                value={editState[r.id].url}
                onChange={(e) => setEditState((prev) => ({ ...prev, [r.id]: { ...prev[r.id], url: e.target.value } }))}
              />
            ) : (
              r.url
            )
          }
        />
        <Table.Column<SocialLink>
          title={t("social:colActions")}
          render={(_, r, index) => (
            <Space>
              {editState[r.id] !== undefined ? (
                <>
                  <Button size="small" type="primary" onClick={() => saveEdit(r.id)}>{t("social:save")}</Button>
                  <Button size="small" onClick={() => cancelEdit(r.id)}>✕</Button>
                </>
              ) : (
                <Button size="small" onClick={() => startEdit(r)}>{t("social:edit")}</Button>
              )}
              <Button size="small" disabled={index === 0} onClick={() => void move(index, -1)}>{t("social:moveUp")}</Button>
              <Button size="small" disabled={index === rows.length - 1} onClick={() => void move(index, 1)}>{t("social:moveDown")}</Button>
              <Button size="small" danger onClick={() => remove(r.id)}>{t("social:delete")}</Button>
            </Space>
          )}
        />
      </Table>

      {newRow !== null ? (
        <Space style={{ marginTop: 16 }}>
          <Input
            placeholder={t("social:colNetwork")}
            value={newRow.network}
            onChange={(e) => setNewRow((prev) => prev ? { ...prev, network: e.target.value } : prev)}
          />
          <Input
            placeholder={t("social:colUrl")}
            value={newRow.url}
            onChange={(e) => setNewRow((prev) => prev ? { ...prev, url: e.target.value } : prev)}
          />
          <Button type="primary" onClick={saveNew}>{t("social:save")}</Button>
          <Button onClick={() => setNewRow(null)}>✕</Button>
        </Space>
      ) : (
        <Button style={{ marginTop: 16 }} onClick={() => setNewRow({ network: "", url: "" })}>
          {t("social:add")}
        </Button>
      )}
    </div>
  );
};
