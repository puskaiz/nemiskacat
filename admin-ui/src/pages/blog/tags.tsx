import { TaxonomyList } from "../../components/TaxonomyList";
export const BlogTags = () => (
  <TaxonomyList resource="blog/tags" titleKey="taxonomy:blogTags" basePath="/blog/tags" addLabelKey="taxonomy:addTag" />
);
