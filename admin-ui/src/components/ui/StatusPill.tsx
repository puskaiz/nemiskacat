import { useThemeMode } from "../../theme/ThemeProvider";
import { PILL_DARK, PILL_LIGHT } from "../../theme/palette";
import { toneFor } from "./tone";

// Status pill matching the prototype's table/detail pills: a squared (radius 3)
// tone-colored span, no dot (Admin Mid-fi HU.dc.html, e.g. line 192).
export function StatusPill({ status, label }: { status: string; label?: string }) {
  const { mode } = useThemeMode();
  const c = (mode === "dark" ? PILL_DARK : PILL_LIGHT)[toneFor(status)];
  return (
    <span style={{ display: "inline-block", borderRadius: 3, padding: "3px 11px", fontSize: 12, fontWeight: 600, color: c.fg, background: c.bg, whiteSpace: "nowrap" }}>
      {label ?? status}
    </span>
  );
}
