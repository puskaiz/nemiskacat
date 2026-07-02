package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.application.workshop.WorkshopService.WorkshopImageView;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** Workshop + session CRUD for the admin SPA (T24 admin / T15). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class WorkshopAdminController {

    private final WorkshopService workshops;
    private final AuditService audit;

    public record WorkshopRequest(@NotBlank String name, @NotBlank String slug,
                                  String description, @Min(0) int vatRatePercent,
                                  ProductStatus status) {
    }

    public record SessionRequest(@NotNull OffsetDateTime startAt, @Min(0) int capacity,
                                 @Min(0) long priceHuf, @NotBlank String sku) {
    }

    public record WorkshopImageDto(Long id, String url, String alt, int position, boolean featured) {
        static WorkshopImageDto of(WorkshopImageView v) {
            return new WorkshopImageDto(v.id(), v.url(), v.alt(), v.position(), v.featured());
        }
    }

    public record WorkshopView(Long id, String name, String slug, String description,
                               Integer vatRatePercent, ProductStatus status, long sessionCount,
                               List<WorkshopImageDto> images) {
    }

    private WorkshopView toView(Product p) {
        List<WorkshopImageDto> imgs = workshops.images(p).stream().map(WorkshopImageDto::of).toList();
        return new WorkshopView(p.getId(), p.getName(), p.getSlug(), p.getDescription(),
                p.getVatRatePercent(), p.getStatus(), workshops.sessionCount(p.getId()), imgs);
    }

    public record SessionView(Long id, OffsetDateTime startAt, int capacity, Long priceHuf, String sku) {
        static SessionView of(WorkshopSession s) {
            Variant v = s.getVariant();
            return new SessionView(s.getId(), s.getStartAt(), s.getCapacity(), v.getRegularPriceHuf(), v.getSku());
        }
    }

    @GetMapping("/workshops")
    public List<WorkshopView> list(HttpServletResponse response) {
        List<WorkshopView> views = workshops.listWorkshops().stream().map(this::toView).toList();
        response.setHeader("X-Total-Count", String.valueOf(views.size()));
        return views;
    }

    @GetMapping("/workshops/{id}")
    public WorkshopView get(@PathVariable Long id) {
        return toView(workshops.getWorkshop(id));
    }

    @PostMapping("/workshops")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkshopView create(@RequestBody @Valid WorkshopRequest req) {
        Product ws = workshops.createWorkshop(req.name(), req.slug(), req.description(), req.vatRatePercent());
        audit.record("WORKSHOP_CREATE", "workshop", String.valueOf(ws.getId()), req.name());
        return toView(ws);
    }

    @PutMapping("/workshops/{id}")
    public WorkshopView update(@PathVariable Long id, @RequestBody @Valid WorkshopRequest req) {
        Product ws = workshops.updateWorkshop(id, req.name(), req.slug(), req.description(),
                req.vatRatePercent(), req.status());
        audit.record("WORKSHOP_UPDATE", "workshop", String.valueOf(id), req.name());
        return toView(ws);
    }

    @DeleteMapping("/workshops/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workshops.deleteWorkshop(id);
        audit.record("WORKSHOP_DELETE", "workshop", String.valueOf(id), null);
    }

    @GetMapping("/workshops/{id}/sessions")
    public List<SessionView> sessions(@PathVariable Long id, HttpServletResponse response) {
        List<SessionView> views = workshops.listSessions(id).stream().map(SessionView::of).toList();
        response.setHeader("X-Total-Count", String.valueOf(views.size()));
        return views;
    }

    @GetMapping("/workshops/{id}/bookings")
    public List<hu.deposoft.webshop.application.workshop.WorkshopBookingView> bookings(
            @PathVariable Long id, HttpServletResponse response) {
        var views = workshops.bookings(id);
        response.setHeader("X-Total-Count", String.valueOf(views.size()));
        return views;
    }

    @PostMapping("/workshops/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView addSession(@PathVariable Long id, @RequestBody @Valid SessionRequest req) {
        WorkshopSession s = workshops.addSession(id, req.startAt(), req.capacity(), req.priceHuf(), req.sku());
        audit.record("SESSION_ADD", "workshop", String.valueOf(id), req.sku());
        return SessionView.of(s);
    }

    @PutMapping("/sessions/{sessionId}")
    public SessionView updateSession(@PathVariable Long sessionId, @RequestBody @Valid SessionRequest req) {
        WorkshopSession s = workshops.updateSession(sessionId, req.startAt(), req.capacity(), req.priceHuf(), req.sku());
        audit.record("SESSION_UPDATE", "session", String.valueOf(sessionId), req.sku());
        return SessionView.of(s);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSession(@PathVariable Long sessionId) {
        workshops.cancelSession(sessionId);
        audit.record("SESSION_CANCEL", "session", String.valueOf(sessionId), null);
    }
}
