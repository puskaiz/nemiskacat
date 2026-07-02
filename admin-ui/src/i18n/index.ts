import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import type { I18nProvider } from "@refinedev/core";
import huNav from "./hu/nav.json";
import huCommon from "./hu/common.json";
import enNav from "./en/nav.json";
import enCommon from "./en/common.json";
import huReports from "./hu/reports.json";
import enReports from "./en/reports.json";
import huProducts from "./hu/products.json";
import enProducts from "./en/products.json";
import huCategories from "./hu/categories.json";
import enCategories from "./en/categories.json";
import huCoupons from "./hu/coupons.json";
import enCoupons from "./en/coupons.json";
import huCustomers from "./hu/customers.json";
import enCustomers from "./en/customers.json";
import huShipping from "./hu/shipping.json";
import enShipping from "./en/shipping.json";
import huInstructors from "./hu/instructors.json";
import enInstructors from "./en/instructors.json";
import huCms from "./hu/cms.json";
import enCms from "./en/cms.json";
import huBookings from "./hu/bookings.json";
import enBookings from "./en/bookings.json";
import huSettings from "./hu/settings.json";
import enSettings from "./en/settings.json";
import huOrders from "./hu/orders.json";
import enOrders from "./en/orders.json";
import huDashboard from "./hu/dashboard.json";
import enDashboard from "./en/dashboard.json";
import huWorkshops from "./hu/workshops.json";
import enWorkshops from "./en/workshops.json";
import huAuth from "./hu/auth.json";
import enAuth from "./en/auth.json";
import huBlog from "./hu/blog.json";
import enBlog from "./en/blog.json";
import huSidebarBlocks from "./hu/sidebar-blocks.json";
import enSidebarBlocks from "./en/sidebar-blocks.json";
import huSocial from "./hu/social.json";
import enSocial from "./en/social.json";
import huTaxonomy from "./hu/taxonomy.json";
import enTaxonomy from "./en/taxonomy.json";
import huPages from "./hu/pages.json";
import enPages from "./en/pages.json";

export const LANG_KEY = "admin-lang";
// Guard on the method, not just the global: some test runtimes expose a partial
// `localStorage` stub ({}), so `typeof localStorage !== "undefined"` is not enough.
const hasStorage = typeof localStorage !== "undefined" && typeof localStorage.getItem === "function";
const stored = hasStorage ? localStorage.getItem(LANG_KEY) : null;

export const i18n = i18next.createInstance();
i18n.use(initReactI18next).init({
  resources: {
    hu: {
      nav: huNav, common: huCommon, reports: huReports, products: huProducts,
      categories: huCategories, coupons: huCoupons, customers: huCustomers,
      shipping: huShipping, instructors: huInstructors, cms: huCms,
      bookings: huBookings, settings: huSettings, orders: huOrders, dashboard: huDashboard,
      workshops: huWorkshops, auth: huAuth, blog: huBlog, "sidebar-blocks": huSidebarBlocks,
      social: huSocial, taxonomy: huTaxonomy, pages: huPages,
    },
    en: {
      nav: enNav, common: enCommon, reports: enReports, products: enProducts,
      categories: enCategories, coupons: enCoupons, customers: enCustomers,
      shipping: enShipping, instructors: enInstructors, cms: enCms,
      bookings: enBookings, settings: enSettings, orders: enOrders, dashboard: enDashboard,
      workshops: enWorkshops, auth: enAuth, blog: enBlog, "sidebar-blocks": enSidebarBlocks,
      social: enSocial, taxonomy: enTaxonomy, pages: enPages,
    },
  },
  lng: stored === "en" || stored === "hu" ? stored : "hu",
  fallbackLng: "hu",
  ns: [
    "common", "nav", "reports", "products", "categories", "coupons",
    "customers", "shipping", "instructors", "cms", "bookings", "settings", "orders", "dashboard",
    "workshops", "auth", "blog", "sidebar-blocks", "social", "taxonomy", "pages",
  ],
  defaultNS: "common",
  interpolation: { escapeValue: false },
});

/** Refine i18nProvider backed by the shared i18next instance. */
export const i18nProvider: I18nProvider = {
  // Refine calls translate(key, options, defaultMessage) — and sometimes the
  // 2-arg form translate(key, defaultMessage). Honour the default so any key we
  // haven't translated falls back to Refine's built-in text instead of showing
  // the raw key (e.g. "notifications.deleteSuccess").
  translate: (key, options, defaultMessage) => {
    if (typeof options === "string") {
      return i18n.t(key, { defaultValue: options }) as string;
    }
    return i18n.t(key, {
      defaultValue: defaultMessage,
      ...(options as Record<string, unknown> | undefined),
    }) as string;
  },
  changeLocale: async (lang) => {
    if (typeof localStorage !== "undefined" && typeof localStorage.setItem === "function") {
      localStorage.setItem(LANG_KEY, lang);
    }
    await i18n.changeLanguage(lang);
  },
  getLocale: () => i18n.language,
};
