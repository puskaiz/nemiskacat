/**
 * Api layer: thin REST controllers serving admin, POS, the static-page session
 * islands and the product feeds. JSON only, RFC 9457 problem+json for errors
 * (added with the first real endpoints). Controllers delegate to the
 * {@code application} services and hold no business logic.
 */
package hu.deposoft.webshop.api;
