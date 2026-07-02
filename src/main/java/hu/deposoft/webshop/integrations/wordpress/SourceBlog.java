package hu.deposoft.webshop.integrations.wordpress;

import java.util.List;

/** One blog snapshot to import, source-agnostic (JSON export now). The export file is
 *  {@code { "posts": [ ... ], "categories": [ ... ], "tags": [ ... ] }}. */
public record SourceBlog(List<SourceBlogPost> posts, List<SourceBlogCategory> categories,
                         List<SourceBlogTag> tags) {
}
