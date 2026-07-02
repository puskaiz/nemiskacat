import { TaxonomyList } from "../../components/TaxonomyList";
export const BlogCategories = () => (
  <TaxonomyList resource="blog/categories" titleKey="taxonomy:blogCategories" basePath="/blog/categories" addLabelKey="taxonomy:addCategory" />
);
