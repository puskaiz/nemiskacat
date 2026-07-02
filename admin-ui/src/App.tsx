import { Authenticated, Refine } from "@refinedev/core";
import { ErrorComponent, useNotificationProvider } from "@refinedev/antd";
import routerProvider, { CatchAllNavigate, NavigateToResource } from "@refinedev/react-router-v6";
import { BrowserRouter, Outlet, Route, Routes, useParams } from "react-router-dom";
import { App as AntdApp } from "antd";
import { ThemeProvider } from "./theme/ThemeProvider";
import { AdminLayout } from "./components/layout/AdminLayout";
import { i18nProvider } from "./i18n";
import "@refinedev/antd/dist/reset.css";

import { authProvider } from "./providers/authProvider";
import { dataProvider } from "./providers/dataProvider";
import { Dashboard } from "./pages/dashboard";
import { Login } from "./pages/login";
import { OrderList } from "./pages/orders/list";
import { OrderShow } from "./pages/orders/show";
import { WorkshopList } from "./pages/workshops/list";
import { WorkshopCreate } from "./pages/workshops/create";
import { WorkshopEdit } from "./pages/workshops/edit";
import { Products } from "./pages/products";
import { ProductShow } from "./pages/products/show";
import { Categories } from "./pages/categories";
import { Coupons } from "./pages/coupons";
import { Customers } from "./pages/customers";
import { Shipping } from "./pages/shipping";
import { Instructors } from "./pages/instructors";
import { Bookings } from "./pages/bookings";
import { Reports } from "./pages/reports";
import { PageList } from "./pages/pages/list";
import { PageEdit } from "./pages/pages/edit";
import { Settings } from "./pages/settings";
import { BlogList } from "./pages/blog";
import { BlogEdit } from "./pages/blog/edit";
import { SidebarBlockList } from "./pages/sidebar-blocks/list";
import { SidebarBlockEdit } from "./pages/sidebar-blocks/edit";
import { SocialSettings } from "./pages/settings/social";
import { BlogCategories } from "./pages/blog/categories";
import { BlogTags } from "./pages/blog/tags";
import { ProductTags } from "./pages/products/tags";
import { TaxonomyEdit } from "./components/TaxonomyEdit";

function SettingsWithTab() {
  const { tab } = useParams();
  return <Settings tab={tab} />;
}

export const App = () => (
  <BrowserRouter basename="/admin">
    <ThemeProvider>
      <AntdApp>
        <Refine
          routerProvider={routerProvider}
          dataProvider={dataProvider}
          authProvider={authProvider}
          i18nProvider={i18nProvider}
          notificationProvider={useNotificationProvider}
          resources={[
            { name: "dashboard", list: "/", meta: { label: "Irányítópult" } },
            { name: "orders", list: "/orders", show: "/orders/show/:id", meta: { label: "Rendelések" } },
            { name: "products", list: "/products", show: "/products/show/:id", meta: { label: "Termékek" } },
            { name: "categories", list: "/products/categories", create: "/products/categories/create", edit: "/products/categories/edit/:id", meta: { parent: "products", label: "Kategóriák" } },
            { name: "coupons", list: "/coupons", meta: { label: "Kuponok" } },
            { name: "customers", list: "/customers", meta: { label: "Vásárlók" } },
            { name: "shipping", list: "/shipping", meta: { label: "Szállítás" } },
            { name: "workshops", list: "/workshops", create: "/workshops/create", edit: "/workshops/edit/:id", meta: { label: "Workshopok" } },
            { name: "blog/posts", list: "/blog", create: "/blog/create", edit: "/blog/edit/:id", meta: { label: "Blog" } },
            { name: "instructors", list: "/workshops/instructors", meta: { parent: "workshops", label: "Oktatók" } },
            { name: "bookings", list: "/bookings", meta: { label: "Foglalások" } },
            { name: "reports", list: "/reports", meta: { label: "Riportok" } },
            { name: "pages", list: "/pages", create: "/pages/create", edit: "/pages/edit/:id", meta: { label: "Oldalak" } },
            { name: "sidebar-blocks", list: "/sidebar-blocks", edit: "/sidebar-blocks/edit/:id", meta: { label: "Oldalsáv blokkok" } },
            { name: "settings", list: "/settings", meta: { label: "Beállítások" } },
            { name: "settings/social", list: "/settings/social", meta: { label: "Közösségi linkek", parent: "settings" } },
            { name: "blog/categories", list: "/blog/categories", create: "/blog/categories/create", edit: "/blog/categories/edit/:id", meta: { label: "Blog kategóriák", parent: "blog/posts" } },
            { name: "blog/tags", list: "/blog/tags", create: "/blog/tags/create", edit: "/blog/tags/edit/:id", meta: { label: "Blog címkék", parent: "blog/posts" } },
            { name: "products/tags", list: "/products/tags", create: "/products/tags/create", edit: "/products/tags/edit/:id", meta: { label: "Termék címkék", parent: "products" } },
          ]}
          options={{
            syncWithLocation: true,
            disableTelemetry: true,
            textTransformers: { plural: (w: string) => w, singular: (w: string) => w },
          }}
        >
          <Routes>
            <Route
              element={
                <Authenticated key="protected" fallback={<CatchAllNavigate to="/login" />}>
                  <AdminLayout>
                    <Outlet />
                  </AdminLayout>
                </Authenticated>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path="/orders">
                <Route index element={<OrderList />} />
                <Route path="show/:id" element={<OrderShow />} />
              </Route>
              <Route path="/products">
                <Route index element={<Products />} />
                <Route path="categories">
                  <Route index element={<Categories />} />
                  <Route path="create" element={<TaxonomyEdit resource="categories" titleKey="taxonomy:productCategories" basePath="/products/categories" backLabelKey="taxonomy:backProductCategories" />} />
                  <Route path="edit/:id" element={<TaxonomyEdit resource="categories" titleKey="taxonomy:productCategories" basePath="/products/categories" backLabelKey="taxonomy:backProductCategories" />} />
                </Route>
                <Route path="show/:id" element={<ProductShow />} />
              </Route>
              <Route path="/coupons" element={<Coupons />} />
              <Route path="/customers" element={<Customers />} />
              <Route path="/shipping" element={<Shipping />} />
              <Route path="/workshops">
                <Route index element={<WorkshopList />} />
                <Route path="create" element={<WorkshopCreate />} />
                <Route path="edit/:id" element={<WorkshopEdit />} />
                <Route path="instructors" element={<Instructors />} />
              </Route>
              <Route path="/blog">
                <Route index element={<BlogList />} />
                <Route path="create" element={<BlogEdit />} />
                <Route path="edit/:id" element={<BlogEdit />} />
                <Route path="categories">
                  <Route index element={<BlogCategories />} />
                  <Route path="create" element={<TaxonomyEdit resource="blog/categories" titleKey="taxonomy:blogCategories" basePath="/blog/categories" backLabelKey="taxonomy:backBlogCategories" />} />
                  <Route path="edit/:id" element={<TaxonomyEdit resource="blog/categories" titleKey="taxonomy:blogCategories" basePath="/blog/categories" backLabelKey="taxonomy:backBlogCategories" />} />
                </Route>
                <Route path="tags">
                  <Route index element={<BlogTags />} />
                  <Route path="create" element={<TaxonomyEdit resource="blog/tags" titleKey="taxonomy:blogTags" basePath="/blog/tags" backLabelKey="taxonomy:backBlogTags" />} />
                  <Route path="edit/:id" element={<TaxonomyEdit resource="blog/tags" titleKey="taxonomy:blogTags" basePath="/blog/tags" backLabelKey="taxonomy:backBlogTags" />} />
                </Route>
              </Route>
              <Route path="/products/tags">
                <Route index element={<ProductTags />} />
                <Route path="create" element={<TaxonomyEdit resource="products/tags" titleKey="taxonomy:productTags" basePath="/products/tags" backLabelKey="taxonomy:backProductTags" />} />
                <Route path="edit/:id" element={<TaxonomyEdit resource="products/tags" titleKey="taxonomy:productTags" basePath="/products/tags" backLabelKey="taxonomy:backProductTags" />} />
              </Route>
              <Route path="/sidebar-blocks">
                <Route index element={<SidebarBlockList />} />
                <Route path="edit/:id" element={<SidebarBlockEdit />} />
              </Route>
              <Route path="/bookings" element={<Bookings />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/pages">
                <Route index element={<PageList />} />
                <Route path="create" element={<PageEdit />} />
                <Route path="edit/:id" element={<PageEdit />} />
              </Route>
              <Route path="/settings">
                <Route index element={<Settings />} />
                <Route path="social" element={<SocialSettings />} />
                <Route path=":tab" element={<SettingsWithTab />} />
              </Route>
              <Route path="*" element={<ErrorComponent />} />
            </Route>
            <Route
              element={
                <Authenticated key="public" fallback={<Outlet />}>
                  <NavigateToResource resource="dashboard" />
                </Authenticated>
              }
            >
              <Route path="/login" element={<Login />} />
            </Route>
          </Routes>
        </Refine>
      </AntdApp>
    </ThemeProvider>
  </BrowserRouter>
);
