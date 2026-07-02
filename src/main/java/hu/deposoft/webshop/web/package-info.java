/**
 * Web layer: thin Thymeleaf controllers for the customer-facing pages and blog.
 * Rendered HTML must stay session-independent and cacheable (CLAUDE.md rule #2);
 * cart/login state comes only from the shared client-side session island.
 */
package hu.deposoft.webshop.web;
