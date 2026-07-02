package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.customer.CustomerAdminQueryService;
import hu.deposoft.webshop.application.customer.CustomerAdminQueryService.CustomerSummary;
import hu.deposoft.webshop.application.customer.CustomerAdminQueryService.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only customer list for the admin SPA. ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class CustomerAdminController {

    private final CustomerAdminQueryService query;

    @GetMapping
    public List<CustomerSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            HttpServletResponse response) {
        PageResult result = query.list(page, size, q);
        response.setHeader("X-Total-Count", String.valueOf(result.total()));
        return result.items();
    }
}
