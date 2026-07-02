package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.blog.BlogAdminService;
import hu.deposoft.webshop.application.blog.BlogAdminService.PostDetail;
import hu.deposoft.webshop.application.blog.BlogAdminService.PostSummary;
import hu.deposoft.webshop.application.blog.BlogAdminService.PostUpsert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlogPostController {

    private final BlogAdminService service;

    @GetMapping("/api/admin/blog/posts")
    public List<PostSummary> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  HttpServletResponse response) {
        Page<PostSummary> result = service.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.getTotalElements()));
        return result.getContent();
    }

    @GetMapping("/api/admin/blog/posts/{id}")
    public PostDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/admin/blog/posts")
    public PostDetail create(@RequestBody PostUpsert cmd) {
        return service.create(cmd);
    }

    @PutMapping("/api/admin/blog/posts/{id}")
    public PostDetail update(@PathVariable Long id, @RequestBody PostUpsert cmd) {
        return service.update(id, cmd);
    }

    @DeleteMapping("/api/admin/blog/posts/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/api/admin/blog/posts/{id}/publish")
    public PostDetail publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/api/admin/blog/posts/{id}/unpublish")
    public PostDetail unpublish(@PathVariable Long id) {
        return service.unpublish(id);
    }

    @PostMapping(value = "/api/admin/blog/posts/{id}/cover", consumes = "multipart/form-data")
    public PostDetail cover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return service.uploadCover(id, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
