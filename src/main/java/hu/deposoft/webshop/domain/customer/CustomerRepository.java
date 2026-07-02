package hu.deposoft.webshop.domain.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByWpUserId(Long wpUserId);

    @Query("SELECT c FROM Customer c WHERE lower(c.email) = lower(:email)")
    Optional<Customer> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT c FROM Customer c WHERE lower(c.username) = lower(:username)")
    Optional<Customer> findByUsernameIgnoreCase(@Param("username") String username);

    /**
     * Admin customer search: {@code q} nullable, matches email + name parts by substring, newest first.
     *
     * <p>Native query rationale (mirrors {@link OrderRepository} / ProductRepository): a bare {@code null}
     * bind in a JPQL {@code :q IS NULL OR ...} filter leaves {@code :q} untyped, and PostgreSQL resolves
     * it to {@code bytea} once the statement is server-prepared — {@code function lower(bytea) does not
     * exist}. {@code CAST(:q AS text)} makes the type explicit. The {@code countQuery} must be kept in
     * sync with the main WHERE clause.
     */
    @Query(value = """
            SELECT * FROM customer c
            WHERE CAST(:q AS text) IS NULL
               OR lower(c.email) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.first_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.last_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.display_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
            ORDER BY c.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM customer c
            WHERE CAST(:q AS text) IS NULL
               OR lower(c.email) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.first_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.last_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
               OR lower(coalesce(c.display_name,'')) LIKE lower(concat('%', CAST(:q AS text), '%'))
            """,
            nativeQuery = true)
    Page<Customer> search(@Param("q") String q, Pageable pageable);
}
