import { useCreate, useDelete, useOne, useUpdate } from "@refinedev/core";
import { App, Button, Form, Input, Popconfirm } from "antd";
import { type CSSProperties } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import type { Taxonomy } from "../types";
import { SEMANTIC } from "../theme/palette";

interface Props {
  resource: string;
  titleKey: string;
  basePath: string;
  /** i18n key for the "← Back to …" link; defaults to the section title. */
  backLabelKey?: string;
}

const card: CSSProperties = {
  background: "var(--surface)",
  border: "1px solid var(--border)",
  borderRadius: 2,
  boxShadow: "0 1px 2px rgba(16,24,40,.04)",
  padding: "20px 24px",
  maxWidth: 480,
};

export function TaxonomyEdit({ resource, titleKey, basePath, backLabelKey }: Props) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const nav = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = id != null;

  const [form] = Form.useForm<{ name: string; slug: string }>();

  // Load existing record in edit mode — gate with enabled so we don't fire in create mode
  const { data, isLoading } = useOne<Taxonomy>({
    resource,
    id: id ?? "",
    queryOptions: { enabled: isEdit },
  });
  const record = data?.data;

  const { mutate: doCreate } = useCreate<Taxonomy>();
  const { mutate: doUpdate } = useUpdate<Taxonomy>();
  const { mutate: doDelete } = useDelete();

  // In edit mode, wait until the record is loaded to render the form so antd
  // does not flip from uncontrolled to controlled inputs.
  if (isEdit && (isLoading || !record)) {
    return <div style={{ padding: 32, color: "var(--muted)" }}>…</div>;
  }

  const pageTitle = isEdit
    ? `${t(titleKey)} — ${t("taxonomy:editTitle")}`
    : `${t(titleKey)} — ${t("taxonomy:newTitle")}`;

  const handleSave = async () => {
    let values: { name: string; slug: string };
    try {
      values = await form.validateFields();
    } catch {
      return;
    }

    if (isEdit) {
      // slug is immutable — send name only
      doUpdate(
        { resource, id: id!, values: { name: values.name } },
        {
          onSuccess: () => {
            message.success(t("taxonomy:saved"));
          },
          onError: () => message.error(t("taxonomy:saveFailed")),
        },
      );
    } else {
      doCreate(
        { resource, values: { name: values.name, slug: values.slug } },
        {
          onSuccess: (result) => {
            message.success(t("taxonomy:saved"));
            nav(`${basePath}/edit/${result.data.id}`);
          },
          onError: () => message.error(t("taxonomy:saveFailed")),
        },
      );
    }
  };

  const handleDelete = () => {
    doDelete(
      { resource, id: id! },
      {
        onSuccess: () => {
          nav(basePath);
        },
        onError: () => message.error(t("taxonomy:saveFailed")),
      },
    );
  };

  // Red pill matching the app's logout button — same semantic colour token.
  const deletePill: CSSProperties = {
    background: SEMANTIC.signout,
    color: "#fff",
    borderRadius: 999,
    padding: "9px 16px",
    fontSize: 13,
    fontWeight: 600,
    border: "none",
    cursor: "pointer",
    display: "inline-flex",
    alignItems: "center",
    gap: 8,
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18, maxWidth: 600 }}>
      {/* Back button — same style/approach as the Products page */}
      <div
        onClick={() => nav(basePath)}
        style={{
          fontSize: 13,
          color: "var(--accent-fg)",
          fontWeight: 600,
          cursor: "pointer",
          marginBottom: 12,
        }}
      >
        ← {t(backLabelKey ?? titleKey)}
      </div>

      {/* Header — title left, action buttons grouped on the right */}
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: "-.3px" }}>
          {pageTitle}
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Button type="primary" onClick={() => void handleSave()}>
            {t("taxonomy:save")}
          </Button>
          {isEdit && (
            <Popconfirm
              title={t("taxonomy:deleteConfirm")}
              onConfirm={handleDelete}
              okType="danger"
              okText={t("taxonomy:delete")}
              cancelText={t("taxonomy:cancel")}
            >
              <button type="button" style={deletePill}>
                {t("taxonomy:delete")}
              </button>
            </Popconfirm>
          )}
        </div>
      </div>

      {/* Form card */}
      <div style={card}>
        <Form
          form={form}
          layout="vertical"
          // key forces re-mount when record loads, avoiding uncontrolled→controlled warning
          key={isEdit ? (record?.id ?? "loading") : "create"}
          initialValues={
            isEdit && record
              ? { name: record.name, slug: record.slug }
              : undefined
          }
        >
          <Form.Item
            label={t("taxonomy:colName")}
            name="name"
            rules={[{ required: true, message: t("taxonomy:namePlaceholder") }]}
          >
            <Input placeholder={t("taxonomy:namePlaceholder")} />
          </Form.Item>

          <Form.Item
            label={t("taxonomy:colSlug")}
            name="slug"
            rules={
              !isEdit
                ? [{ required: true, message: t("taxonomy:slugPlaceholder") }]
                : []
            }
            extra={isEdit ? t("taxonomy:slugReadOnly") : undefined}
          >
            <Input
              placeholder={t("taxonomy:slugPlaceholder")}
              disabled={isEdit}
            />
          </Form.Item>
        </Form>
      </div>
    </div>
  );
}
