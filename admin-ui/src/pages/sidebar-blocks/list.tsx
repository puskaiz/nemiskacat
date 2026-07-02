import { useTable } from "@refinedev/core";
import { Button, Space, Switch, Table, message } from "antd";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { API_BASE, apiFetch } from "../../api/http";
import type { SidebarBlock } from "../../types";
import { BLOCK_LABELS, type BlockType } from "./content";

export const SidebarBlockList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { tableQueryResult } = useTable<SidebarBlock>({
    resource: "sidebar-blocks",
    pagination: { mode: "off" },
  });
  const rows = [...(tableQueryResult.data?.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);
  const refetch = () => tableQueryResult.refetch();

  const toggle = async (b: SidebarBlock) => {
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/${b.id}/${b.enabled ? "disable" : "enable"}`, { method: "POST" });
    if (res.ok) { await refetch(); } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  const move = async (index: number, dir: -1 | 1) => {
    const swap = index + dir;
    if (swap < 0 || swap >= rows.length) return;
    const order = rows.map((r) => r.id);
    [order[index], order[swap]] = [order[swap], order[index]];
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/reorder`, {
      method: "POST",
      body: JSON.stringify({ blockIds: order }),
    });
    if (res.ok) { await refetch(); } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  return (
    <div>
      <h1>{t("sidebar-blocks:title")}</h1>
      <Table dataSource={rows} rowKey="id" pagination={false}>
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colType")} dataIndex="blockType"
          render={(v: BlockType) => BLOCK_LABELS[v]} />
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colEnabled")} dataIndex="enabled"
          render={(_, r) => <Switch checked={r.enabled} onChange={() => void toggle(r)} />} />
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colActions")}
          render={(_, r, index) => (
            <Space>
              <Button size="small" disabled={index === 0} onClick={() => void move(index, -1)}>{t("sidebar-blocks:moveUp")}</Button>
              <Button size="small" disabled={index === rows.length - 1} onClick={() => void move(index, 1)}>{t("sidebar-blocks:moveDown")}</Button>
              <Button size="small" type="primary" onClick={() => navigate(`/sidebar-blocks/edit/${r.id}`)}>{t("sidebar-blocks:edit")}</Button>
            </Space>
          )} />
      </Table>
    </div>
  );
};
