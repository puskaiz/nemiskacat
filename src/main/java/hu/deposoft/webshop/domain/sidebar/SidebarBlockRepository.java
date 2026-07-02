package hu.deposoft.webshop.domain.sidebar;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SidebarBlockRepository extends JpaRepository<SidebarBlock, Long> {
    List<SidebarBlock> findByEnabledTrueOrderByDisplayOrderAsc();
    List<SidebarBlock> findAllByOrderByDisplayOrderAsc();
}
