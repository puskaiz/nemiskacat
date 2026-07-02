// Faithful port of the sidebar nav from `Admin Mid-fi HU.dc.html` (the `icons`
// map + `navDefs` + `groupOrder`, lines ~909-981). `iconPath` is the EXACT
// inline-SVG path the prototype draws; `labelKey` resolves against the i18n `nav`
// namespace so the admin stays bilingual; `route` is the real react-router path.
export interface NavChild {
  labelKey: string;
  route: string;
}
export interface NavItem {
  id: string;
  labelKey: string;
  route: string;
  iconPath: string;
  badge?: string; // exact prototype value, e.g. "4"
  children?: NavChild[];
  bottom?: boolean; // pinned to the bottom (Settings)
}
export interface NavRow {
  headerKey?: string; // section header rendered above this item
  item: NavItem;
}

// Exact SVG path strings from the prototype `icons` map (1.7 stroke, round caps).
const ICON = {
  dashboard: "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z",
  orders: "M8 4h8a1 1 0 011 1v15l-3-2-2 2-2-2-2 2-2-2V5a1 1 0 011-1z M9 8h6 M9 12h6",
  products: "M3 7l9-4 9 4-9 4-9-4z M3 7v10l9 4 9-4V7",
  workshops: "M7 3v4 M17 3v4 M4 9h16 M5 5h14a1 1 0 011 1v13a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z",
  bookings: "M9 11l3 3 8-8 M21 12v7a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1h11",
  customers: "M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2 M9 11a4 4 0 100-8 4 4 0 000 8 M22 21v-2a4 4 0 00-3-3.87 M16 3.13a4 4 0 010 7.75",
  coupons: "M19 5L5 19 M9 6.5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0 M20 17.5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0",
  shipping: "M3 6h11v9H3z M14 9h4l3 3v3h-7z M7.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z M17.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z",
  reports: "M4 20V10 M10 20V4 M16 20v-7 M21 20H3",
  cms: "M14 3H6a1 1 0 00-1 1v16a1 1 0 001 1h12a1 1 0 001-1V8z M14 3v5h5 M8 13h8 M8 17h6",
  blog: "M5 4h11l3 3v13a1 1 0 01-1 1H5a1 1 0 01-1-1V5a1 1 0 011-1z M15 4v4h4 M8 13h8 M8 17h6",
  settings: "M4 21v-6 M4 11V3 M12 21v-8 M12 9V3 M20 21v-4 M20 13V3 M1 15h6 M9 9h6 M17 17h6",
} as const;

// groupOrder + navDefs from the prototype, mapped to routes. Badges are the
// exact prototype values { orders:4, products:5, shipping:9, bookings:3 }.
export const NAV: NavRow[] = [
  { item: { id: "dashboard", labelKey: "nav:dashboard", route: "/", iconPath: ICON.dashboard } },
  { headerKey: "nav:sell", item: { id: "orders", labelKey: "nav:orders", route: "/orders", iconPath: ICON.orders, badge: "4" } },
  { item: { id: "products", labelKey: "nav:products", route: "/products", iconPath: ICON.products, children: [
    { labelKey: "nav:allProducts", route: "/products" },
    { labelKey: "nav:categories", route: "/products/categories" },
    { labelKey: "nav:tags", route: "/products/tags" },
  ] } },
  { item: { id: "coupons", labelKey: "nav:coupons", route: "/coupons", iconPath: ICON.coupons } },
  { item: { id: "customers", labelKey: "nav:customers", route: "/customers", iconPath: ICON.customers } },
  { item: { id: "shipping", labelKey: "nav:shipping", route: "/shipping", iconPath: ICON.shipping } },
  { headerKey: "nav:workshopsGroup", item: { id: "workshops", labelKey: "nav:workshops", route: "/workshops", iconPath: ICON.workshops, children: [
    { labelKey: "nav:allWorkshops", route: "/workshops" },
    { labelKey: "nav:instructors", route: "/workshops/instructors" },
  ] } },
  { item: { id: "bookings", labelKey: "nav:bookings", route: "/bookings", iconPath: ICON.bookings } },
  { headerKey: "nav:analytics", item: { id: "reports", labelKey: "nav:reports", route: "/reports", iconPath: ICON.reports } },
  { headerKey: "nav:content", item: { id: "blog", labelKey: "nav:blog", route: "/blog", iconPath: ICON.blog, children: [
    { labelKey: "nav:allBlog", route: "/blog" },
    { labelKey: "nav:categories", route: "/blog/categories" },
    { labelKey: "nav:tags", route: "/blog/tags" },
  ] } },
  { item: { id: "cms", labelKey: "nav:pages", route: "/pages", iconPath: ICON.cms } },
  { item: { id: "sidebar-blocks", labelKey: "nav:sidebarBlocks", route: "/sidebar-blocks", iconPath: ICON.cms } },
  { item: { id: "settings/social", labelKey: "nav:social", route: "/settings/social", iconPath: ICON.settings } },
  { item: { id: "settings", labelKey: "nav:settings", route: "/settings", iconPath: ICON.settings, bottom: true } },
];
