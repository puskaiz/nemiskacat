import { useEffect, useState, type CSSProperties } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useCreate, useOne, useUpdate } from "@refinedev/core";
import { App, Button, Form, Input, Segmented, Switch } from "antd";
import DOMPurify from "dompurify";
import { useTranslation } from "react-i18next";
import { apiFetch, API_BASE } from "../../api/http";
import { HtmlEditor } from "../../components/HtmlEditor";

interface PageUpsert {
  slug: string;
  title: string;
  bodyHtml: string;
  seoTitle: string | null;
  seoDescription: string | null;
}

interface PageDetail {
  id: number;
  slug: string;
  title: string;
  bodyHtml: string;
  status: string;
  seoTitle: string | null;
  seoDescription: string | null;
}

type ContentView = "edit" | "preview" | "split";

const box: CSSProperties = {
  background: "var(--surface)",
  border: "1px solid var(--border)",
  borderRadius: 2,
  boxShadow: "0 1px 2px rgba(16,24,40,.04)",
  padding: 18,
  display: "flex",
  flexDirection: "column",
  gap: 16,
};
const boxTitle: CSSProperties = { fontSize: 13, fontWeight: 700 };

export function PageEdit() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = id != null;

  const [form] = Form.useForm<PageUpsert>();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string>("DRAFT");
  const [bodyHtml, setBodyHtml] = useState<string>("");
  const [contentView, setContentView] = useState<ContentView>("edit");

  const { data: pageData } = useOne<PageDetail>({
    resource: "pages",
    id: id ?? "",
    queryOptions: { enabled: isEdit },
  });
  const loaded = pageData?.data;

  useEffect(() => {
    if (loaded) {
      setStatus(loaded.status);
      setBodyHtml(loaded.bodyHtml ?? "");
    }
  }, [loaded]);

  const { mutate: createPage } = useCreate<PageDetail>();
  const { mutate: updatePage } = useUpdate<PageDetail>();

  const handleSave = async () => {
    let values: Omit<PageUpsert, never>;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (isEdit) {
      updatePage(
        { resource: "pages", id: id!, values },
        {
          onSuccess: () => message.success(t("pages:saved")),
          onError: (err) => message.error((err as { message?: string }).message ?? t("pages:saveFailed")),
        },
      );
    } else {
      createPage(
        { resource: "pages", values },
        {
          onSuccess: (result) => {
            message.success(t("pages:created"));
            navigate(`/pages/edit/${result.data.id}`);
          },
          onError: (err) => message.error((err as { message?: string }).message ?? t("pages:saveFailed")),
        },
      );
    }
  };

  const handlePublish = () => {
    if (!isEdit) return;
    setBusy(true);
    apiFetch(`${API_BASE}/pages/${id}/${status === "PUBLISHED" ? "unpublish" : "publish"}`, { method: "POST" })
      .then((res) => {
        if (res.ok) {
          setStatus(status === "PUBLISHED" ? "DRAFT" : "PUBLISHED");
        } else {
          return res.text().then((txt) => message.error(txt || t("pages:saveFailed")));
        }
      })
      .catch(() => message.error(t("pages:saveFailed")))
      .finally(() => setBusy(false));
  };

  const handleBodyChange = (html: string) => {
    setBodyHtml(html);
    form.setFieldValue("bodyHtml", html);
  };

  const showEditor = contentView === "edit" || contentView === "split";
  const showPreview = contentView === "preview" || contentView === "split";

  const previewPane = (
    <div
      className="blog-preview-body"
      style={{
        flex: 1, minWidth: 0, overflow: "auto", border: "1px solid var(--border)",
        borderRadius: 6, padding: "16px 18px", background: "var(--surface)",
        fontSize: 15, lineHeight: 1.65, color: "var(--text)",
      }}
    >
      {bodyHtml.trim() ? (
        <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(bodyHtml) }} />
      ) : (
        <div style={{ color: "var(--faint)" }}>{t("pages:previewEmpty")}</div>
      )}
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 900 }}>
      <div onClick={() => navigate("/pages")} style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>
        {t("pages:back")}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{ fontSize: 23, fontWeight: 700, letterSpacing: "-.3px" }}>
          {isEdit ? t("pages:editTitle") : t("pages:createTitle")}
        </div>
        <div style={{ flex: 1 }} />
        {isEdit && (
          <Switch
            checked={status === "PUBLISHED"}
            onChange={handlePublish}
            disabled={busy}
            checkedChildren={t("pages:statusPublished")}
            unCheckedChildren={t("pages:statusDraft")}
          />
        )}
        <Button type="primary" onClick={handleSave} disabled={busy}>{t("pages:save")}</Button>
        <Button onClick={() => navigate("/pages")}>{t("pages:cancel")}</Button>
      </div>

      <Form
        form={form}
        layout="vertical"
        key={isEdit ? (loaded?.id ?? "loading") : "create"}
        initialValues={loaded ? {
          title: loaded.title,
          slug: loaded.slug,
          bodyHtml: loaded.bodyHtml,
          seoTitle: loaded.seoTitle ?? "",
          seoDescription: loaded.seoDescription ?? "",
        } : undefined}
        style={{ display: "flex", flexDirection: "column", gap: 16 }}
      >
        <div style={box}>
          <div style={boxTitle}>{t("pages:boxBasics")}</div>
          <Form.Item label={t("pages:fieldTitle")} name="title" rules={[{ required: true, message: t("pages:fieldRequired") }]} style={{ marginBottom: 0 }}>
            <Input />
          </Form.Item>
          <Form.Item
            label={t("pages:fieldSlug")}
            name="slug"
            rules={[
              { required: true, message: t("pages:fieldRequired") },
              { pattern: /^[a-z0-9-]+$/, message: t("pages:slugPattern") },
            ]}
            extra={isEdit ? t("pages:slugReadOnly") : undefined}
            style={{ marginBottom: 0 }}
          >
            <Input disabled={isEdit} />
          </Form.Item>
        </div>

        <div style={box}>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div style={boxTitle}>{t("pages:boxContent")}</div>
            <div style={{ flex: 1 }} />
            <Segmented<ContentView>
              size="small"
              value={contentView}
              onChange={(v) => setContentView(v)}
              options={[
                { label: t("pages:viewEdit"), value: "edit" },
                { label: t("pages:viewPreview"), value: "preview" },
                { label: t("pages:viewSplit"), value: "split" },
              ]}
            />
          </div>
          <Form.Item name="bodyHtml" noStyle>
            <input type="hidden" />
          </Form.Item>
          <div style={{ display: "flex", gap: 16, alignItems: "stretch", flexWrap: "wrap" }}>
            {showEditor && (
              <div style={{ flex: 1, minWidth: 0, overflow: "auto" }}>
                <HtmlEditor value={bodyHtml} onChange={handleBodyChange} />
              </div>
            )}
            {showPreview && previewPane}
          </div>
        </div>

        <div style={box}>
          <div style={boxTitle}>{t("pages:boxSeo")}</div>
          <Form.Item label={t("pages:fieldSeoTitle")} name="seoTitle" style={{ marginBottom: 0 }}>
            <Input />
          </Form.Item>
          <Form.Item label={t("pages:fieldSeoDescription")} name="seoDescription" style={{ marginBottom: 0 }}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </div>
      </Form>
    </div>
  );
}
