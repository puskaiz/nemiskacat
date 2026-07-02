import { useForm } from "@refinedev/antd";
import { Form, Input, InputNumber } from "antd";
import { type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import { RichTextEditor } from "../../components/RichTextEditor";
import type { Workshop } from "../../types";

// --- Shared style tokens (mirrors products/show.tsx and orders/show.tsx) -----
const card: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const sideCard: CSSProperties = { ...card, padding: 16, display: "flex", flexDirection: "column", gap: 12 };
const sideTitle: CSSProperties = { fontSize: 13, fontWeight: 700 };
const accentBtn: CSSProperties = { border: 0, borderRadius: 999, padding: "10px 22px", fontSize: 14, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer", fontFamily: "inherit" };
const sectionTitle: CSSProperties = { fontSize: 13, fontWeight: 700, marginBottom: 12 };

export const WorkshopCreate = () => {
  const nav = useNavigate();
  const { formProps, saveButtonProps } = useForm<Workshop>();

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
        <div style={{ fontSize: 23, fontWeight: 700 }}>Új workshop</div>
        <div style={{ flex: 1 }} />
        {/* saveButtonProps.onClick calls form.submit() → formProps.onFinish → Refine POST mutation.
            Extract only onClick/disabled to avoid spreading antd-specific props onto a native element. */}
        <button
          type="button"
          onClick={saveButtonProps.onClick}
          disabled={saveButtonProps.disabled}
          style={{ ...accentBtn, cursor: saveButtonProps.disabled ? "wait" : "pointer" }}
        >
          Mentés
        </button>
      </div>

      {/*
        Single <Form> wraps BOTH columns so all Form.Items share one form
        instance and saveButtonProps.onClick collects all fields in one onFinish.
      */}
      <Form {...formProps} layout="vertical">
        <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
          {/* Left column: main content */}
          <div style={{ flex: 1.6, ...card, padding: 18, display: "flex", flexDirection: "column", gap: 4 }}>
            <div style={sectionTitle}>Adatok</div>
            <Form.Item label="Név" name="name" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item
              label="Slug"
              name="slug"
              rules={[{ required: true, pattern: /^[a-z0-9-]+$/, message: "Kisbetű, szám, kötőjel" }]}
            >
              <Input placeholder="butorfestes-workshop" />
            </Form.Item>
            <Form.Item label="Leírás" name="description">
              <RichTextEditor />
            </Form.Item>
          </div>

          {/* Right column: settings */}
          <div style={{ flex: 1 }}>
            <div style={sideCard}>
              <div style={sideTitle}>Beállítások</div>
              <Form.Item
                label="ÁFA %"
                name="vatRatePercent"
                initialValue={27}
                rules={[{ required: true }]}
              >
                <InputNumber min={0} max={27} style={{ width: "100%" }} />
              </Form.Item>
            </div>
          </div>
        </div>
      </Form>
    </div>
  );
};
