package hu.deposoft.webshop.integrations.wordpress;

import java.util.List;

/** One content-page snapshot to import (JSON export): {@code { "pages": [ ... ] }}. */
public record SourcePages(List<SourcePage> pages) {
}
