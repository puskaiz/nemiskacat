// Hand-written types mirroring the /api/admin DTOs (slice 1B; no OpenAPI codegen).

export type WorkshopStatus = "PUBLISHED" | "DRAFT";

export interface WorkshopImage {
  id: number;
  url: string;
  alt?: string | null;
  position: number;
  featured: boolean;
}

export interface Workshop {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  vatRatePercent: number | null;
  status: WorkshopStatus;
  sessionCount: number;
  images: WorkshopImage[];
}

export interface WorkshopSession {
  id: number;
  startAt: string; // ISO offset date-time
  capacity: number;
  priceHuf: number | null;
  sku: string;
}

export interface WorkshopBooking {
  sessionId: number | null;
  sessionStartAt: string | null;
  sessionSku: string;
  orderNumber: string;
  orderStatus: OrderStatus;
  customerName: string;
  email: string;
  phone: string | null;
  seats: number;
  orderItemId: number;
  cancelledSeats: number;
  lineGrossHuf: number;
  unitGrossHuf: number;
}

export type OrderStatus =
  | "NEW"
  | "PAID"
  | "PACKING"
  | "SHIPPED"
  | "COMPLETED"
  | "CANCELLED"
  | "REFUNDED"
  | "PROCESSING"
  | "ON_HOLD"
  | "FAILED"
  | "AWAITING_SHIPMENT";

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  customerName: string;
  email: string;
  totalGrossHuf: number;
  createdAt: string;
}

export interface OrderLine {
  productName: string;
  variantLabel: string | null;
  sku: string | null;
  quantity: number;
  lineGrossHuf: number;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  customerName: string;
  email: string;
  phone: string | null;
  postcode: string;
  city: string;
  addressLine: string;
  shipMethodName: string;
  shipGrossHuf: number;
  itemsGrossHuf: number;
  totalGrossHuf: number;
  createdAt: string;
  lines: OrderLine[];
}

export type CustomerRole = "CUSTOMER" | "SUBSCRIBER" | "ADMIN";

export interface CustomerSummary {
  id: number;
  name: string; // full name (may fall back to email)
  email: string;
  role: CustomerRole;
  enabled: boolean;
  createdAt: string; // ISO offset date-time (UTC)
}

export interface AdminIdentity {
  email: string;
  role: string;
  productEditorEnabled: boolean;
}

export type ProductStatus = "PUBLISHED" | "DRAFT";

export interface ProductSummary {
  id: number;
  name: string;
  slug: string;
  primaryCategory: string;
  categories: ProductCategoryRef[];
  priceGrossHuf: number | null;
  stockQty: number;
  status: ProductStatus;
  variantCount: number;
  coverImageUrl: string | null;
  sku: string | null;
}

export interface AttributeValueRef {
  id: number;
  attributeId: number;
  attributeLabel: string;
  valueLabel: string;
}

export interface AttributeValueOption {
  id: number;
  slug: string;
  label: string;
}

export interface AttributeView {
  id: number;
  slug: string;
  label: string;
  values: AttributeValueOption[];
}

export interface CreateVariant {
  sku: string | null;
  attributeValueIds: number[];
}

export interface UpdateVariant {
  sku: string | null;
  attributeValueIds: number[];
}

export interface ProductVariantView {
  id: number;
  label: string;
  sku: string;
  regularPriceHuf: number | null;
  salePriceHuf: number | null;
  stockQty: number;
  lowStockThreshold: number;
  attributeValues: AttributeValueRef[];
}

export interface ProductCategoryRef {
  name: string;
  slug: string;
}

export interface ProductImageView {
  id: number;
  url: string;
  alt: string | null;
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  status: ProductStatus;
  shortDescription: string | null;
  description: string | null;
  seoTitle: string | null;
  metaDescription: string | null;
  vatRatePercent: number | null;
  effectiveVatRatePercent: number | null;
  categories: ProductCategoryRef[];
  images: ProductImageView[];
  variants: ProductVariantView[];
}

export type PriceBasis = "NET" | "GROSS";

export interface PriceInput {
  amount: number;
  basis: PriceBasis;
}

export interface PriceUpdate {
  regular: PriceInput | null;
  sale: PriceInput | null;
  saleFrom: string | null; // ISO instant (UTC)
  saleTo: string | null;
}

export interface SidebarBlock {
  id: number;
  blockType: "AUTHOR" | "CATEGORIES" | "CTA" | "CONTACT" | "SOCIAL" | "INSTAGRAM";
  displayOrder: number;
  enabled: boolean;
  content: string; // type-specific JSON
}

export interface CategoryVisibility {
  name: string;
  slug: string;
  sidebarHidden: boolean;
}

export interface SocialLink { id: number; network: string; url: string; displayOrder: number; }

export interface Taxonomy { id: number; name: string; slug: string; }
