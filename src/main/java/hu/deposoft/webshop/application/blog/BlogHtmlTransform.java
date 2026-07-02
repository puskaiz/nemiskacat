package hu.deposoft.webshop.application.blog;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalises WordPress post HTML for storage as rich HTML (WYSIWYG path,
 * replacing the Markdown path — see ADR 0008/0009; the former HtmlToMarkdown
 * converter has been removed).
 *
 * <p>Operations performed:
 * <ol>
 *   <li>Strip Gutenberg {@code <!-- wp:* -->} and {@code <!-- /wp:* -->} block comments.</li>
 *   <li>Replace {@code <div class="wp-block-columns">} blocks whose columns
 *       contain only images (no text-only column) with
 *       {@code <div class="nk-figure-row">} containing one {@code <figure>} per
 *       image — {@code <img src="..." alt="...">} plus an optional
 *       {@code <figcaption>} (only when a {@code wp-element-caption} element is
 *       present and non-blank). Original image {@code src} values are preserved;
 *       the importer (B2) rewrites URLs afterwards.</li>
 *   <li>All other content is passed through untouched.</li>
 * </ol>
 *
 * <p>Detection logic ({@link #isImageColumns}) is ported verbatim from
 * the former HtmlToMarkdown converter (now removed), which proved it in
 * production imports.
 */
@Component
public class BlogHtmlTransform {

    /**
     * Normalise WordPress post HTML.
     *
     * @param html raw WordPress HTML, may be {@code null} or blank
     * @return normalised HTML, or {@code ""} for null/blank input
     */
    public String normalize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        stripWpBlockComments(doc);
        replaceImageColumnBlocks(doc);

        return doc.body().html();
    }

    // -------------------------------------------------------------------------
    // Step 1: strip Gutenberg block comments
    // -------------------------------------------------------------------------

    private static void stripWpBlockComments(Document doc) {
        // Collect first; removing during traversal would corrupt the iterator.
        List<Node> toRemove = new ArrayList<>();
        doc.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Comment comment) {
                    String data = comment.getData().stripLeading();
                    if (data.startsWith("wp:") || data.startsWith("/wp:")) {
                        toRemove.add(comment);
                    }
                }
            }
        });
        toRemove.forEach(Node::remove);
    }

    // -------------------------------------------------------------------------
    // Step 2: replace image-column blocks with .nk-figure-row
    // -------------------------------------------------------------------------

    private static void replaceImageColumnBlocks(Document doc) {
        for (Element cols : doc.select("div.wp-block-columns")) {
            if (!isImageColumns(cols)) {
                continue;
            }
            cols.replaceWith(buildFigureRow(cols));
        }
    }

    /**
     * A columns block is an image layout when it has images and no text-only column.
     * Ported verbatim from the former HtmlToMarkdown converter (now removed).
     */
    private static boolean isImageColumns(Element cols) {
        boolean anyImage = false;
        for (Element child : cols.children()) {
            if (!child.hasClass("wp-block-column")) {
                continue;
            }
            if (child.selectFirst("img") != null) {
                anyImage = true;
            } else if (!child.text().isBlank()) {
                return false; // a column with real text is not a pure image layout
            }
        }
        return anyImage;
    }

    /**
     * Build the {@code .nk-figure-row} element: one {@code <figure>} per image,
     * in document order. A {@code <figcaption>} is added only when the source
     * figure contains a non-blank {@code .wp-element-caption}.
     */
    private static Element buildFigureRow(Element cols) {
        Element row = new Element("div").addClass("nk-figure-row");
        for (Element img : cols.select("img")) {
            if (!img.hasAttr("src")) {
                continue;
            }
            Element figure = new Element("figure");

            Element imgOut = new Element("img")
                    .attr("src", img.attr("src"));
            if (img.hasAttr("alt")) {
                imgOut.attr("alt", img.attr("alt"));
            }
            figure.appendChild(imgOut);

            // WordPress image blocks always wrap <img> in <figure>; if absent we still emit the image, only the caption lookup is skipped.
            // Look for a figcaption inside the closest <figure> ancestor in the source.
            Element srcFigure = img.closest("figure");
            if (srcFigure != null) {
                Element caption = srcFigure.selectFirst(".wp-element-caption");
                if (caption != null && !caption.text().isBlank()) {
                    figure.appendChild(new Element("figcaption").text(caption.text().trim()));
                }
            }

            row.appendChild(figure);
        }
        return row;
    }
}
