import { useEffect, useRef, useState, type ChangeEvent, type CSSProperties } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useCreate, useOne, useUpdate } from "@refinedev/core";
import { App, Button, Form, Input, Segmented, Switch } from "antd";
import DOMPurify from "dompurify";
import { useTranslation } from "react-i18next";
import { apiFetch, API_BASE } from "../../api/http";
import { ProductSelect } from "../../components/ProductSelect";
import { CategorySelect } from "../../components/CategorySelect";
import { HtmlEditor } from "../../components/HtmlEditor";
import type { ProductCategoryRef } from "../../types";

// Blog post create + edit form.
// - In CREATE mode (no :id param): all fields editable, including slug.
//   Cover upload and publish toggle become available only after first save
//   (redirect to /blog/edit/:id after create).
// - In EDIT mode (:id present): slug is READ-ONLY (URL slugs are immutable per CLAUDE.md #7).
//   Cover upload and publish/unpublish are available.
//
// Non-CRUD calls (cover, publish) use apiFetch directly.
//
// Visual style mirrors the product editor (src/pages/products/show.tsx): fields
// are grouped into bordered "boxes" built from plain divs + inline styles + the
// var(--…) design tokens (see `box`/`boxTitle`/`fieldLabel` below).

interface PostUpsert {
  slug: string;
  title: string;
  excerpt: string | null;
  bodyHtml: string;
  seoTitle: string | null;
  seoDescription: string | null;
  categorySlugs: string[];
  recommendedSkus: string[];
}

interface PostDetail {
  id: number;
  slug: string;
  title: string;
  excerpt: string | null;
  bodyHtml: string;
  coverImageUrl: string | null;
  status: string;
  seoTitle: string | null;
  seoDescription: string | null;
  categorySlugs: string[];
  recommendedSkus: string[];
}

interface BlogCategory {
  id: number;
  name: string;
  slug: string;
}

type ContentView = "edit" | "preview" | "split";

// --- Shared box aesthetic, ported from the product editor -------------------
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
// Two-column edit layout mirroring the product editor: main content (left) and
// meta sidebar (right) both scale proportionally with the viewport width
// (left ~1.6 : right 1), so the sidebar keeps a usable width at any size.
const leftCol: CSSProperties = {
  flex: 1.6,
  minWidth: 0,
  display: "flex",
  flexDirection: "column",
  gap: 16,
};
const rightCol: CSSProperties = {
  flex: 1,
  minWidth: 0,
  display: "flex",
  flexDirection: "column",
  gap: 16,
};

export function BlogEdit() {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = id != null;

  const [form] = Form.useForm<PostUpsert>();
  const [categorySlugs, setCategorySlugs] = useState<string[]>([]);
  const [recommendedSkus, setRecommendedSkus] = useState<string[]>([]);
  const [blogCategories, setBlogCategories] = useState<BlogCategory[]>([]);
  const [busy, setBusy] = useState(false);
  const [coverUrl, setCoverUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<string>("DRAFT");
  // Controlled copy of the body HTML so the live preview can render as the
  // user types. Kept in sync with the Form field (the Form remains the source
  // of truth on save).
  const [bodyHtml, setBodyHtml] = useState<string>("");
  const [contentView, setContentView] = useState<ContentView>("edit");
  const fileRef = useRef<HTMLInputElement>(null);

  // Load blog categories once
  useEffect(() => {
    apiFetch(`${API_BASE}/blog/categories`)
      .then((r) => r.json())
      .then((cats: BlogCategory[]) => setBlogCategories(cats))
      .catch(() => { /* ignore */ });
  }, []);

  // Load existing post in edit mode
  const { data: postData, refetch } = useOne<PostDetail>({
    resource: "blog/posts",
    id: id ?? "",
    queryOptions: { enabled: isEdit },
  });

  const loaded = postData?.data;

  useEffect(() => {
    if (loaded) {
      setCategorySlugs(loaded.categorySlugs ?? []);
      setRecommendedSkus(loaded.recommendedSkus ?? []);
      setCoverUrl(loaded.coverImageUrl ?? null);
      setStatus(loaded.status);
      setBodyHtml(loaded.bodyHtml ?? "");
    }
  }, [loaded]);

  const { mutate: createPost } = useCreate<PostDetail>();
  const { mutate: updatePost } = useUpdate<PostDetail>();

  const handleSave = async () => {
    let values: Omit<PostUpsert, "categorySlugs" | "recommendedSkus">;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    const payload: PostUpsert = { ...values, categorySlugs, recommendedSkus };

    if (isEdit) {
      updatePost(
        { resource: "blog/posts", id: id!, values: payload },
        {
          onSuccess: () => message.success(t("blog:saved")),
          onError: (err) => message.error((err as { message?: string }).message ?? t("blog:saveFailed")),
        },
      );
    } else {
      createPost(
        { resource: "blog/posts", values: payload },
        {
          onSuccess: (result) => {
            message.success(t("blog:created"));
            navigate(`/blog/edit/${result.data.id}`);
          },
          onError: (err) => message.error((err as { message?: string }).message ?? t("blog:saveFailed")),
        },
      );
    }
  };

  const handleCoverUpload = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !isEdit) return;
    e.target.value = "";
    if (busy) return;
    setBusy(true);
    const fd = new FormData();
    fd.append("file", file);
    apiFetch(`${API_BASE}/blog/posts/${id}/cover`, { method: "POST", body: fd })
      .then((res) => {
        if (res.ok) {
          message.success(t("blog:coverUploaded"));
          refetch();
        } else {
          return res.text().then((txt) => message.error(txt || t("blog:coverFailed")));
        }
      })
      .catch(() => message.error(t("blog:coverFailed")))
      .finally(() => setBusy(false));
  };

  const handlePublish = () => {
    if (!isEdit) return;
    setBusy(true);
    apiFetch(`${API_BASE}/blog/posts/${id}/${status === "PUBLISHED" ? "unpublish" : "publish"}`, { method: "POST" })
      .then((res) => {
        if (res.ok) {
          const next = status === "PUBLISHED" ? "DRAFT" : "PUBLISHED";
          setStatus(next);
          message.success(next === "PUBLISHED" ? t("blog:published") : t("blog:unpublished"));
        } else {
          return res.text().then((txt) => message.error(txt || t("blog:publishFailed")));
        }
      })
      .catch(() => message.error(t("blog:publishFailed")))
      .finally(() => setBusy(false));
  };

  // Keep the controlled bodyHtml state and the Form field in lock-step so the
  // live preview updates while typing and validateFields() sees the latest value.
  const handleBodyChange = (html: string) => {
    setBodyHtml(html);
    form.setFieldValue("bodyHtml", html);
  };

  // CategorySelect expects ProductCategoryRef[] — map BlogCategory to that shape
  const catRefs: ProductCategoryRef[] = blogCategories.map((c) => ({ name: c.name, slug: c.slug }));

  const formTitle = isEdit ? t("blog:editTitle") : t("blog:createTitle");

  const showEditor = contentView === "edit" || contentView === "split";
  const showPreview = contentView === "preview" || contentView === "split";

  const previewPane = (
    <div
      className="blog-preview-body"
      style={{
        flex: 1,
        minWidth: 0,
        overflow: "auto",
        border: "1px solid var(--border)",
        borderRadius: 6,
        padding: "16px 18px",
        background: "var(--surface)",
        fontSize: 15,
        lineHeight: 1.65,
        color: "var(--text)",
      }}
    >
      {bodyHtml.trim() ? (
        <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(bodyHtml) }} />
      ) : (
        <div style={{ color: "var(--faint)" }}>{t("blog:previewEmpty")}</div>
      )}
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {/* Back link — same pattern as the Products page */}
      <div
        onClick={() => navigate("/blog")}
        style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}
      >
        ← {t("blog:back")}
      </div>

      {/* Header */}
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <div style={{ fontSize: 23, fontWeight: 700, letterSpacing: "-.3px" }}>{formTitle}</div>
        <div style={{ flex: 1 }} />
        {isEdit && (
          <Switch
            checked={status === "PUBLISHED"}
            onChange={handlePublish}
            disabled={busy}
            checkedChildren={t("blog:statusPublished")}
            unCheckedChildren={t("blog:statusDraft")}
          />
        )}
        <Button type="primary" onClick={handleSave} disabled={busy}>
          {t("blog:save")}
        </Button>
        <Button onClick={() => navigate("/blog")}>{t("blog:cancel")}</Button>
      </div>

      <Form
        form={form}
        layout="vertical"
        key={isEdit ? (loaded?.id ?? "loading") : "create"}
        initialValues={loaded ? {
          title: loaded.title,
          slug: loaded.slug,
          excerpt: loaded.excerpt ?? "",
          bodyHtml: loaded.bodyHtml,
          seoTitle: loaded.seoTitle ?? "",
          seoDescription: loaded.seoDescription ?? "",
        } : undefined}
        style={{ display: "flex", flexDirection: "row", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}
      >
        {/* LEFT column — main content */}
        <div style={leftCol}>
          {/* Alapadatok box */}
          <div style={box}>
            <div style={boxTitle}>{t("blog:boxBasics")}</div>

            <Form.Item
              label={t("blog:fieldTitle")}
              name="title"
              rules={[{ required: true, message: t("blog:fieldRequired") }]}
              style={{ marginBottom: 0 }}
            >
              <Input />
            </Form.Item>

            <Form.Item
              label={t("blog:fieldSlug")}
              name="slug"
              rules={[
                { required: true, message: t("blog:fieldRequired") },
                { pattern: /^[a-z0-9-]+$/, message: t("blog:slugPattern") },
              ]}
              extra={isEdit ? t("blog:slugReadOnly") : undefined}
              style={{ marginBottom: 0 }}
            >
              <Input disabled={isEdit} />
            </Form.Item>

            <Form.Item label={t("blog:fieldExcerpt")} name="excerpt" style={{ marginBottom: 0 }}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </div>

          {/* Tartalom box — editor / preview / split */}
          <div style={box}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={boxTitle}>{t("blog:boxContent")}</div>
              <div style={{ flex: 1 }} />
              <Segmented<ContentView>
                size="small"
                value={contentView}
                onChange={(v) => setContentView(v)}
                options={[
                  { label: t("blog:viewEdit"), value: "edit" },
                  { label: t("blog:viewPreview"), value: "preview" },
                  { label: t("blog:viewSplit"), value: "split" },
                ]}
              />
            </div>

            {/* The Form.Item keeps `bodyHtml` in the form; the HtmlEditor and the
                live preview are both wired to the controlled `bodyHtml` state. */}
            <Form.Item name="bodyHtml" noStyle>
              <input type="hidden" />
            </Form.Item>

            <div
              style={{
                display: "flex",
                gap: 16,
                alignItems: "stretch",
                flexWrap: "wrap",
              }}
            >
              {showEditor && (
                <div style={{ flex: 1, minWidth: 0, overflow: "auto" }}>
                  <HtmlEditor value={bodyHtml} onChange={handleBodyChange} />
                </div>
              )}
              {showPreview && previewPane}
            </div>
          </div>
        </div>

        {/* RIGHT column — SEO + taxonomy + recommendations + cover */}
        <div style={rightCol}>
          {/* SEO box */}
          <div style={box}>
            <div style={boxTitle}>{t("blog:boxSeo")}</div>
            <Form.Item label={t("blog:fieldSeoTitle")} name="seoTitle" style={{ marginBottom: 0 }}>
              <Input />
            </Form.Item>
            <Form.Item label={t("blog:fieldSeoDescription")} name="seoDescription" style={{ marginBottom: 0 }}>
              <Input.TextArea rows={2} />
            </Form.Item>
          </div>

          {/* Blog category multiselect */}
          <div style={box}>
            <div style={boxTitle}>{t("blog:fieldCategories")}</div>
            <CategorySelect categories={catRefs} value={categorySlugs} onChange={setCategorySlugs} placeholderText={t("blog:categorySearch")} removeTitle={t("blog:categoryRemove")} />
          </div>

          {/* Recommended products picker */}
          <div style={box}>
            <div style={boxTitle}>{t("blog:fieldRecommended")}</div>
            <ProductSelect value={recommendedSkus} onChange={setRecommendedSkus} />
          </div>

          {/* Cover image upload — only in edit mode */}
          {isEdit && (
            <div style={box}>
              <div style={boxTitle}>{t("blog:fieldCover")}</div>
              {coverUrl && (
                <img
                  src={coverUrl}
                  alt={t("blog:coverAlt")}
                  style={{ maxWidth: "100%", maxHeight: 200, objectFit: "cover", borderRadius: 4, display: "block" }}
                />
              )}
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                style={{ display: "none" }}
                onChange={handleCoverUpload}
              />
              <div>
                <Button size="small" disabled={busy} onClick={() => fileRef.current?.click()}>
                  {t("blog:coverUpload")}
                </Button>
              </div>
            </div>
          )}
        </div>
      </Form>
    </div>
  );
}
