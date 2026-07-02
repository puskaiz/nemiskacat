package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductImageService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Flag-gated product image gallery endpoints (P2c). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/products/{id}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    public record ReorderRequest(List<Long> imageIds) {}

    @PostMapping(consumes = "multipart/form-data")
    public ProductDetailView upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) {
        try {
            return imageService.upload(id, file.getBytes(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DeleteMapping("/{imageId}")
    public ProductDetailView delete(@PathVariable Long id, @PathVariable Long imageId) {
        return imageService.delete(id, imageId);
    }

    @PostMapping("/{imageId}/cover")
    public ProductDetailView setCover(@PathVariable Long id, @PathVariable Long imageId) {
        return imageService.setCover(id, imageId);
    }

    @PostMapping("/reorder")
    public ProductDetailView reorder(@PathVariable Long id, @RequestBody ReorderRequest request) {
        return imageService.reorder(id, request.imageIds());
    }
}
