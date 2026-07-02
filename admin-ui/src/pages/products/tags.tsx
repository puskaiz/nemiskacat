import { TaxonomyList } from "../../components/TaxonomyList";
export const ProductTags = () => (
  <TaxonomyList resource="products/tags" titleKey="taxonomy:productTags" basePath="/products/tags" addLabelKey="taxonomy:addTag" />
);
