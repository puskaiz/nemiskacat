/**
 * Application layer: the service tier and single source of truth for business
 * logic (CLAUDE.md rule #1). Both the Thymeleaf ({@code web}) and REST
 * ({@code api}) adapters call into the same services here; logic is never
 * duplicated in a controller.
 */
package hu.deposoft.webshop.application;
