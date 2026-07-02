package hu.deposoft.webshop.domain.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByToken(String token);

    Optional<Cart> findByUserId(Long userId);
}
