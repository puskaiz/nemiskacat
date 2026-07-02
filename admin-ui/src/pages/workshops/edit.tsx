import { useForm } from "@refinedev/antd";
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { apiFetch, API_BASE } from "../../api/http";
import { RichTextEditor } from "../../components/RichTextEditor";
import { STATUS_COLORS, STATUS_LABELS, cancelBooking, rescheduleBooking } from "../../api/orders";
import type { Workshop, WorkshopBooking, WorkshopImage, WorkshopSession } from "../../types";

const huf = (n: number | null) =>
  n == null ? "—" : `${n.toLocaleString("hu-HU")} Ft`;

// --- Shared style tokens (mirrors products/show.tsx and orders/show.tsx) -----
const card: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const sideCard: CSSProperties = { ...card, padding: 16, display: "flex", flexDirection: "column", gap: 12 };
const fieldLabel: CSSProperties = { fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 };
const sideTitle: CSSProperties = { fontSize: 13, fontWeight: 700 };
const accentBtn: CSSProperties = { border: 0, borderRadius: 999, padding: "10px 22px", fontSize: 14, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer", fontFamily: "inherit" };
const sectionTitle: CSSProperties = { fontSize: 13, fontWeight: 700, marginBottom: 12 };

// --- Gallery editor ---------------------------------------------------------
// Reuses the product image endpoints with the WORKSHOP's id (a workshop is a
// Product). After each mutation the parent refetches the workshop so the
// gallery reflects the new image list. Mirrors the product gallery editor.
function GalleryEditor({
  workshopId,
  images,
  refetch,
}: {
  workshopId: number;
  images: WorkshopImage[];
  refetch: () => void;
}) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const withBusy = async (fn: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    try {
      await fn();
    } finally {
      setBusy(false);
    }
  };

  const handleUpload = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";
    void withBusy(async () => {
      const fd = new FormData();
      fd.append("file", file);
      const res = await apiFetch(`${API_BASE}/products/${workshopId}/images`, { method: "POST", body: fd });
      if (res.ok) {
        message.success(t("workshops:imgUploaded"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("workshops:imgUploadFailed")));
      }
    });
  };

  const handleDelete = (imgId: number) => {
    void withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${workshopId}/images/${imgId}`, { method: "DELETE" });
      if (res.ok) {
        message.success(t("workshops:imgDeleted"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("workshops:imgDeleteFailed")));
      }
    });
  };

  const handleSetCover = (imgId: number) => {
    void withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${workshopId}/images/${imgId}/cover`, { method: "POST" });
      if (res.ok) {
        message.success(t("workshops:imgCoverSet"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("workshops:imgCoverFailed")));
      }
    });
  };

  const handleMove = (index: number, direction: -1 | 1) => {
    const newOrder = images.map((img) => img.id);
    const swapWith = index + direction;
    [newOrder[index], newOrder[swapWith]] = [newOrder[swapWith], newOrder[index]];
    void withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${workshopId}/images/reorder`, {
        method: "POST",
        body: JSON.stringify({ imageIds: newOrder }),
      });
      if (res.ok) {
        message.success(t("workshops:imgReordered"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("workshops:imgReorderFailed")));
      }
    });
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 10, gap: 10 }}>
        <div style={fieldLabel}>{t("workshops:gallery")}</div>
        <div style={{ flex: 1 }} />
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style={{ display: "none" }}
          onChange={handleUpload}
        />
        <Button type="primary" size="small" disabled={busy} onClick={() => fileRef.current?.click()}>
          {t("workshops:imgUpload")}
        </Button>
      </div>

      {images.length > 0 ? (
        <div style={{ display: "flex", gap: 12, alignItems: "flex-start", flexWrap: "wrap" }}>
          {images.map((img, i) => {
            const isCover = img.featured || i === 0;
            return (
              <div key={img.id} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
                <div style={{ width: 96, height: 96, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)", position: "relative", overflow: "hidden" }}>
                  <img src={img.url} alt={img.alt ?? ""} style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
                  {isCover && (
                    <span style={{ position: "absolute", top: 5, left: 5, background: "var(--accent)", color: "#fff", fontSize: 10, fontWeight: 600, borderRadius: 3, padding: "1px 6px" }}>
                      {t("workshops:cover")}
                    </span>
                  )}
                </div>
                <Space size={4} wrap style={{ justifyContent: "center" }}>
                  <Button size="small" disabled={busy || i === 0} onClick={() => handleMove(i, -1)}>
                    {t("workshops:imgMoveLeft")}
                  </Button>
                  <Button size="small" disabled={busy || i === images.length - 1} onClick={() => handleMove(i, 1)}>
                    {t("workshops:imgMoveRight")}
                  </Button>
                  {!isCover && (
                    <Button size="small" disabled={busy} onClick={() => handleSetCover(img.id)}>
                      {t("workshops:imgMakeCover")}
                    </Button>
                  )}
                  <Popconfirm title={t("workshops:imgDelete")} onConfirm={() => handleDelete(img.id)}>
                    <Button size="small" danger disabled={busy}>
                      {t("workshops:imgDelete")}
                    </Button>
                  </Popconfirm>
                </Space>
              </div>
            );
          })}
        </div>
      ) : (
        <div style={{ color: "var(--muted)", fontSize: 14 }}>{t("workshops:noImages")}</div>
      )}
    </div>
  );
}

// --- Sessions (add / delete already wired; this adds inline edit) -----------
function Sessions({ workshopId }: { workshopId: number }) {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const [sessions, setSessions] = useState<WorkshopSession[]>([]);
  const [bookings, setBookings] = useState<WorkshopBooking[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [editing, setEditing] = useState<WorkshopSession | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [sRes, bRes] = await Promise.all([
        apiFetch(`${API_BASE}/workshops/${workshopId}/sessions`),
        apiFetch(`${API_BASE}/workshops/${workshopId}/bookings`),
      ]);
      setSessions(sRes.ok ? ((await sRes.json()) as WorkshopSession[]) : []);
      setBookings(bRes.ok ? ((await bRes.json()) as WorkshopBooking[]) : []);
    } finally {
      setLoading(false);
    }
  }, [workshopId]);

  // attendees grouped per session (alkalmanként); the expandable row renders them
  const bookingsBySession = useMemo(() => {
    const map = new Map<number, WorkshopBooking[]>();
    for (const b of bookings) {
      if (b.sessionId == null) continue;
      const list = map.get(b.sessionId) ?? [];
      list.push(b);
      map.set(b.sessionId, list);
    }
    return map;
  }, [bookings]);

  const bookedSeats = (sessionId: number) =>
    (bookingsBySession.get(sessionId) ?? []).reduce(
      (sum, b) => sum + (b.seats - b.cancelledSeats),
      0,
    );

  useEffect(() => {
    void reload();
  }, [reload]);

  const add = async (values: {
    startAt: dayjs.Dayjs;
    capacity: number;
    priceHuf: number;
    sku: string;
  }) => {
    const res = await apiFetch(`${API_BASE}/workshops/${workshopId}/sessions`, {
      method: "POST",
      body: JSON.stringify({
        startAt: values.startAt.toISOString(),
        capacity: values.capacity,
        priceHuf: values.priceHuf,
        sku: values.sku,
      }),
    });
    if (res.ok) {
      message.success("Alkalom hozzáadva");
      form.resetFields();
      await reload();
    } else {
      message.error(`Nem sikerült (${res.status})`);
    }
  };

  const openEdit = (s: WorkshopSession) => {
    setEditing(s);
    editForm.setFieldsValue({
      startAt: dayjs(s.startAt),
      capacity: s.capacity,
      priceHuf: s.priceHuf,
      sku: s.sku,
    });
  };

  const submitEdit = async () => {
    if (editing == null) return;
    const values = (await editForm.validateFields()) as {
      startAt: dayjs.Dayjs;
      capacity: number;
      priceHuf: number;
      sku: string;
    };
    const res = await apiFetch(`${API_BASE}/sessions/${editing.id}`, {
      method: "PUT",
      body: JSON.stringify({
        startAt: values.startAt.toISOString(),
        capacity: values.capacity,
        priceHuf: values.priceHuf,
        sku: values.sku,
      }),
    });
    if (res.ok) {
      message.success(t("workshops:sessionSaved"));
      setEditing(null);
      await reload();
    } else {
      const text = await res.text().catch(() => "");
      message.error(text || t("workshops:sessionSaveFailed"));
    }
  };

  const cancelBookingLine = async (b: WorkshopBooking) => {
    const res = await cancelBooking(b.orderItemId);
    if (res.ok) {
      message.success("Jelentkező lemondva, visszatérítés elindítva");
      await reload();
    } else {
      const text = await res.text();
      message.error(text || `Nem sikerült (${res.status})`);
    }
  };

  const [moving, setMoving] = useState<WorkshopBooking | null>(null);
  const [moveTarget, setMoveTarget] = useState<number | null>(null);

  const submitReschedule = async () => {
    if (moving == null || moveTarget == null) return;
    const res = await rescheduleBooking(moving.orderItemId, moveTarget);
    if (res.ok) {
      message.success("Jelentkező áthelyezve");
      setMoving(null);
      setMoveTarget(null);
      await reload();
    } else {
      const text = await res.text();
      message.error(text || `Nem sikerült (${res.status})`);
    }
  };

  const cancel = async (sessionId: number) => {
    const res = await apiFetch(`${API_BASE}/sessions/${sessionId}`, { method: "DELETE" });
    if (res.status === 204) {
      message.success("Alkalom törölve");
      await reload();
    } else if (res.status === 409) {
      message.error("Van rá rendelés — nem törölhető (visszatérítés: 2. szelet).");
    } else {
      message.error(`Nem sikerült (${res.status})`);
    }
  };

  return (
    <div style={{ ...card, padding: 18 }}>
      <div style={sectionTitle}>Alkalmak</div>
      <Table
        dataSource={sessions}
        rowKey="id"
        loading={loading}
        pagination={false}
        size="small"
        expandable={{
          expandedRowRender: (s) => {
            const rows = bookingsBySession.get(s.id) ?? [];
            if (rows.length === 0) {
              return <span style={{ color: "#999" }}>Nincs jelentkező erre az alkalomra.</span>;
            }
            return (
              <Table
                dataSource={rows}
                rowKey={(r) => r.orderNumber}
                pagination={false}
                size="small"
              >
                <Table.Column title="Név" dataIndex="customerName" />
                <Table.Column title="Email" dataIndex="email" />
                <Table.Column
                  title="Telefon"
                  dataIndex="phone"
                  render={(v: string | null) => v ?? "—"}
                />
                <Table.Column title="Helyek" dataIndex="seats" />
                <Table.Column<WorkshopBooking>
                  title="Rendelés"
                  render={(_, r) => (
                    <span>
                      {r.orderNumber} <Tag color={STATUS_COLORS[r.orderStatus]}>{STATUS_LABELS[r.orderStatus]}</Tag>
                    </span>
                  )}
                />
                <Table.Column<WorkshopBooking>
                  title="Művelet"
                  render={(_, r) =>
                    r.cancelledSeats >= r.seats ? (
                      <Tag color="red">Lemondva</Tag>
                    ) : (
                      <Space>
                        <Button size="small" onClick={() => { setMoving(r); setMoveTarget(null); }}>
                          Áthelyezés
                        </Button>
                        <Popconfirm
                          title="Jelentkező lemondása"
                          description={`${r.seats} hely lemondása és ${huf(
                            r.lineGrossHuf,
                          )} visszatérítése?`}
                          okText="Lemondás"
                          cancelText="Mégse"
                          onConfirm={() => cancelBookingLine(r)}
                        >
                          <Button danger size="small">
                            Lemondás
                          </Button>
                        </Popconfirm>
                      </Space>
                    )
                  }
                />
              </Table>
            );
          },
        }}
      >
        <Table.Column
          title="Időpont"
          dataIndex="startAt"
          render={(v: string) => dayjs(v).format("YYYY. MM. DD. HH:mm")}
        />
        <Table.Column<WorkshopSession>
          title="Foglalt"
          render={(_, s) => `${bookedSeats(s.id)} / ${s.capacity}`}
        />
        <Table.Column title="Ár" dataIndex="priceHuf" render={huf} />
        <Table.Column title="Cikkszám" dataIndex="sku" />
        <Table.Column<WorkshopSession>
          title="Művelet"
          render={(_, r) => (
            <Space>
              <Button size="small" onClick={() => openEdit(r)}>
                {t("workshops:sessionEdit")}
              </Button>
              <Popconfirm title="Alkalom törlése?" onConfirm={() => cancel(r.id)}>
                <Button danger size="small">
                  Törlés
                </Button>
              </Popconfirm>
            </Space>
          )}
        />
      </Table>

      <Form form={form} layout="inline" onFinish={add} style={{ marginTop: 16 }}>
        <Form.Item name="startAt" rules={[{ required: true }]}>
          <DatePicker showTime format="YYYY. MM. DD. HH:mm" placeholder="Időpont" />
        </Form.Item>
        <Form.Item name="capacity" rules={[{ required: true }]} initialValue={8}>
          <InputNumber min={0} placeholder="Kapacitás" />
        </Form.Item>
        <Form.Item name="priceHuf" rules={[{ required: true }]} initialValue={15000}>
          <InputNumber min={0} step={500} placeholder="Ár (Ft)" />
        </Form.Item>
        <Form.Item name="sku" rules={[{ required: true }]}>
          <Input placeholder="Cikkszám" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit">
            Alkalom hozzáadása
          </Button>
        </Form.Item>
      </Form>

      <Modal
        title={t("workshops:sessionEditTitle")}
        open={editing != null}
        onCancel={() => setEditing(null)}
        onOk={submitEdit}
        okText={t("workshops:save")}
        cancelText={t("workshops:cancel")}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical">
          <Form.Item label={t("workshops:sessionStartAt")} name="startAt" rules={[{ required: true }]}>
            <DatePicker showTime format="YYYY. MM. DD. HH:mm" style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("workshops:sessionCapacity")} name="capacity" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("workshops:sessionPrice")} name="priceHuf" rules={[{ required: true }]}>
            <InputNumber min={0} step={500} style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("workshops:sessionSku")} name="sku" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Jelentkező áthelyezése"
        open={moving != null}
        onCancel={() => setMoving(null)}
        onOk={submitReschedule}
        okText="Áthelyezés"
        cancelText="Mégse"
        okButtonProps={{ disabled: moveTarget == null }}
      >
        {moving && (
          <Select
            style={{ width: "100%" }}
            placeholder="Válassz cél-alkalmat"
            value={moveTarget ?? undefined}
            onChange={(v) => setMoveTarget(v)}
            options={sessions
              .filter((s) => s.id !== moving.sessionId)
              .map((s) => {
                const free = s.capacity - bookedSeats(s.id);
                const samePrice = (s.priceHuf ?? 0) === moving.unitGrossHuf;
                return {
                  value: s.id,
                  disabled: !samePrice || free < moving.seats - moving.cancelledSeats,
                  label: `${dayjs(s.startAt).format("YYYY. MM. DD. HH:mm")} — ${bookedSeats(s.id)}/${s.capacity} — ${huf(s.priceHuf)}${samePrice ? "" : " (eltérő ár)"}`,
                };
              })}
          />
        )}
      </Modal>
    </div>
  );
}

export const WorkshopEdit = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const { formProps, saveButtonProps, id, queryResult } = useForm<Workshop>();
  const workshop = queryResult?.data?.data;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {/* Back nav */}
      <div
        onClick={() => nav("/workshops")}
        style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}
      >
        ← Vissza a workshopokhoz
      </div>

      {/* Header row: title + Save button */}
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ fontSize: 23, fontWeight: 700 }}>
          {workshop?.name ?? "Workshop szerkesztése"}
        </div>
        <div style={{ flex: 1 }} />
        {/* saveButtonProps.onClick calls form.submit() → formProps.onFinish → Refine PUT mutation.
            Extract only onClick/disabled to avoid spreading antd-specific props onto a native element. */}
        <button
          type="button"
          onClick={saveButtonProps.onClick}
          disabled={saveButtonProps.disabled}
          style={{ ...accentBtn, cursor: saveButtonProps.disabled ? "wait" : "pointer" }}
        >
          {t("workshops:save")}
        </button>
      </div>

      {/*
        Single <Form> wraps BOTH columns so all Form.Items share one form
        instance and saveButtonProps.onClick (which calls form.submit()) collects
        name, slug, description, vatRatePercent, and status in a single onFinish.
      */}
      <Form {...formProps} layout="vertical">
        <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
          {/* Left column: main content */}
          <div style={{ flex: 1.6, display: "flex", flexDirection: "column", gap: 16 }}>
            {/* Details card */}
            <div style={{ ...card, padding: 18, display: "flex", flexDirection: "column", gap: 4 }}>
              <div style={sectionTitle}>Adatok</div>
              <Form.Item label="Név" name="name" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label="Slug" name="slug" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
              <Form.Item label="Leírás" name="description">
                <RichTextEditor />
              </Form.Item>
            </div>
          </div>

          {/* Right column: settings */}
          <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 16 }}>
            <div style={sideCard}>
              <div style={sideTitle}>Beállítások</div>
              <Form.Item label="ÁFA %" name="vatRatePercent" rules={[{ required: true }]}>
                <InputNumber min={0} max={27} style={{ width: "100%" }} />
              </Form.Item>
              <Form.Item label={t("workshops:status")} name="status" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: "PUBLISHED", label: t("workshops:statusPublished") },
                    { value: "DRAFT", label: t("workshops:statusDraft") },
                  ]}
                />
              </Form.Item>
            </div>
          </div>
        </div>
      </Form>

      {/* Sessions table lives outside the main Form to avoid name collisions
          with its own inline Form (session add form). It manages its own state. */}
      {id != null && <Sessions workshopId={Number(id)} />}

      {/* Gallery card — rendered after Alkalmak so Alkalmak appears first */}
      {id != null && (
        <div style={{ ...card, padding: 18 }}>
          <GalleryEditor
            workshopId={Number(id)}
            images={workshop?.images ?? []}
            refetch={() => queryResult?.refetch()}
          />
        </div>
      )}
    </div>
  );
};
