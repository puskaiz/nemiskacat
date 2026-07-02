package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.blog.BlogTagAdminService;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService;
import hu.deposoft.webshop.application.order.BookingCancellationService;
import hu.deposoft.webshop.application.order.BookingRescheduleService;
import hu.deposoft.webshop.application.order.OrderAdminQueryService;
import hu.deposoft.webshop.application.order.OrderAdminService;
import hu.deposoft.webshop.application.order.RefundService;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps admin service errors to HTTP statuses for the SPA. */
@RestControllerAdvice(basePackages = "hu.deposoft.webshop.api.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(WorkshopService.SessionHasBookingsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String bookedSession(WorkshopService.SessionHasBookingsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(WorkshopService.WorkshopHasBookingsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String bookedWorkshop(WorkshopService.WorkshopHasBookingsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(WorkshopService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(WorkshopService.NotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(OrderAdminQueryService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String orderNotFound(OrderAdminQueryService.NotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(OrderAdminService.TransitionNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String transitionNotAllowed(OrderAdminService.TransitionNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(RefundService.RefundNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String refundNotAllowed(RefundService.RefundNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(RefundService.RefundFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String refundFailed(RefundService.RefundFailedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(BookingCancellationService.BookingCancelNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String bookingCancelNotAllowed(BookingCancellationService.BookingCancelNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(BookingCancellationService.BookingRefundFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String bookingRefundFailed(BookingCancellationService.BookingRefundFailedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(BookingRescheduleService.RescheduleNotAllowedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String rescheduleNotAllowed(BookingRescheduleService.RescheduleNotAllowedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String editorDisabled(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException e) {
        return e.getMessage();
    }

    // NOTE: the "8 MB" in the message must stay in sync with spring.servlet.multipart.max-file-size
    // in application.yml — the two are not linked automatically.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String uploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return "A kép túl nagy (max 8 MB).";
    }

    @ExceptionHandler(hu.deposoft.webshop.application.blog.BlogAdminService.SlugConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String blogSlugConflict(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.blog.BlogAdminService.UnknownSkuException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public String blogUnknownSku(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.blog.BlogAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String blogNotFound(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.page.PageAdminService.SlugConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String pageSlugConflict(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.page.PageAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String pageNotFound(RuntimeException e) { return e.getMessage(); }

    @ExceptionHandler(SidebarAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String sidebarBlockNotFound(SidebarAdminService.NotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(hu.deposoft.webshop.application.settings.SocialLinkAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String socialLinkNotFound(hu.deposoft.webshop.application.settings.SocialLinkAdminService.NotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(BlogTagAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String blogTagNotFound(BlogTagAdminService.NotFoundException e) { return e.getMessage(); }

    @ExceptionHandler(ProductTagAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String productTagNotFound(ProductTagAdminService.NotFoundException e) { return e.getMessage(); }

    @ExceptionHandler(hu.deposoft.webshop.application.catalog.ProductCategoryAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String productCategoryNotFound(hu.deposoft.webshop.application.catalog.ProductCategoryAdminService.NotFoundException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String unreadableBody(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return "Hibás vagy hiányzó mező a kérés törzsében.";
    }
}
