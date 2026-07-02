import { useOne, useUpdate } from "@refinedev/core";
import { Button, Form, Input, InputNumber, Switch, Table, message } from "antd";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { API_BASE, apiFetch } from "../../api/http";
import type { CategoryVisibility, SidebarBlock } from "../../types";
import { BLOCK_LABELS, parseContent, serializeContent, type BlockType } from "./content";

export const SidebarBlockEdit = () => {
  const { t } = useTranslation();
  const { id } = useParams();
  const [form] = Form.useForm();
  const { data, isLoading } = useOne<SidebarBlock>({ resource: "sidebar-blocks", id: id ?? "" });
  const { mutate: update } = useUpdate<SidebarBlock>();
  const block = data?.data;
  const type = block?.blockType as BlockType | undefined;

  const [cats, setCats] = useState<CategoryVisibility[]>([]);
  useEffect(() => {
    if (type === "CATEGORIES") {
      void apiFetch(`${API_BASE}/sidebar-blocks/categories`).then(async (r) => {
        if (r.ok) setCats((await r.json()) as CategoryVisibility[]);
      });
    }
  }, [type]);

  if (isLoading || !block || !type) return <div>…</div>;

  const initial = parseContent(type, block.content);

  const save = async () => {
    const values = await form.validateFields();
    update(
      { resource: "sidebar-blocks", id: block.id, values: { content: serializeContent(type, values) } },
      { onSuccess: () => message.success(t("sidebar-blocks:saved")),
        onError: () => message.error(t("sidebar-blocks:saveFailed")) },
    );
  };

  const setCatHidden = async (slug: string, hidden: boolean) => {
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/categories/${slug}/visibility`, {
      method: "POST", body: JSON.stringify({ hidden }),
    });
    if (res.ok) {
      setCats((prev) => prev.map((c) => (c.slug === slug ? { ...c, sidebarHidden: hidden } : c)));
    } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  return (
    <div>
      <h1>{t("sidebar-blocks:editTitle")} — {BLOCK_LABELS[type]}</h1>
      <Form form={form} layout="vertical" initialValues={initial}>
        {type === "AUTHOR" && (<>
          <Form.Item label={t("sidebar-blocks:fieldName")} name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldBio")} name="bio"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldGoals")} name="goals"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldPhotoUrl")} name="photoUrl"><Input /></Form.Item>
        </>)}
        {type === "CTA" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldDescription")} name="description"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldButtonLabel")} name="buttonLabel"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldUrl")} name="url"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldImageUrl")} name="imageUrl"><Input /></Form.Item>
        </>)}
        {type === "CONTACT" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldPhone")} name="phone"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldEmail")} name="email"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldAddress")} name="address"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldOpeningHours")} name="openingHours"><Input.TextArea rows={3} /></Form.Item>
        </>)}
        {type === "SOCIAL" && (
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
        )}
        {type === "CATEGORIES" && (
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
        )}
        {type === "INSTAGRAM" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldCount")} name="count"
                     tooltip={t("sidebar-blocks:fieldCountHint")}>
            <InputNumber min={1} max={12} />
          </Form.Item>
        </>)}
      </Form>

      {type === "CATEGORIES" && (
        <div style={{ marginTop: 24 }}>
          <h2>{t("sidebar-blocks:categories")}</h2>
          <Table dataSource={cats} rowKey="slug" pagination={false}>
            <Table.Column title={t("sidebar-blocks:fieldName")} dataIndex="name" />
            <Table.Column<CategoryVisibility> title={t("sidebar-blocks:catVisible")}
              render={(_, c) => (
                <Switch checked={!c.sidebarHidden}
                        onChange={(checked) => void setCatHidden(c.slug, !checked)} />
              )} />
          </Table>
        </div>
      )}

      <Button type="primary" style={{ marginTop: 16 }} onClick={() => void save()}>
        {t("sidebar-blocks:save")}
      </Button>
    </div>
  );
};
