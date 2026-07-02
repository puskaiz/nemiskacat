import { TaxonomyList } from "../../components/TaxonomyList";

// Product categories now use the shared, editable taxonomy CRUD (same pattern as
// product tags, blog categories, blog tags). List + "Új" button; create/edit
// handled by TaxonomyEdit via the routes in App.tsx. Page size is inherited (10).
export const Categories = () => (
  <TaxonomyList
    resource="categories"
    titleKey="taxonomy:productCategories"
    basePath="/products/categories"
    addLabelKey="taxonomy:addCategory"
  />
);
