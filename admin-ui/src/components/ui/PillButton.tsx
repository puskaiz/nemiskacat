import { Button } from "antd";
import type { ButtonProps } from "antd";
import { SEMANTIC } from "../../theme/palette";

type Variant = "primary" | "secondary" | "danger";
export function PillButton({ variant = "primary", style, ...rest }: Omit<ButtonProps, "variant"> & { variant?: Variant }) {
  const tint =
    variant === "secondary" ? { background: SEMANTIC.secondary, borderColor: SEMANTIC.secondary, color: "#fff" }
    : variant === "danger" ? { background: SEMANTIC.danger, borderColor: SEMANTIC.danger, color: "#fff" }
    : undefined;
  return <Button type={variant === "primary" ? "primary" : "default"} style={{ borderRadius: 999, ...tint, ...style }} {...rest} />;
}
