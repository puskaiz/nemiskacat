package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.AttributeAdminService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.AttributeView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Global attribute catalog (read) + add-term (flag-gated write, in AttributeAdminService). */
@RestController
@RequestMapping("/api/admin/attributes")
@RequiredArgsConstructor
public class AttributeController {

    private final ProductAdminQueryService query;
    private final AttributeAdminService attributeAdmin;

    public record AddValueRequest(String label) {}

    @GetMapping
    public List<AttributeView> list() {
        return query.attributes();
    }

    @PostMapping("/{attributeId}/values")
    public AttributeView addValue(@PathVariable Long attributeId, @RequestBody AddValueRequest body) {
        return attributeAdmin.addValue(attributeId, body.label());
    }
}
