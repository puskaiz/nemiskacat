package hu.deposoft.webshop.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test for AdminExceptionHandler — no Spring context required. */
class AdminExceptionHandlerTest {

    private final AdminExceptionHandler handler = new AdminExceptionHandler();

    @Test
    void uploadTooLargeReturnsHungarianMessage() {
        String body = handler.uploadTooLarge(new MaxUploadSizeExceededException(8_000_000L));
        assertThat(body).isEqualTo("A kép túl nagy (max 8 MB).");
    }

    @Test
    void uploadTooLargeMethodMapsTo413() throws NoSuchMethodException {
        ResponseStatus annotation = AdminExceptionHandler.class
                .getMethod("uploadTooLarge", MaxUploadSizeExceededException.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
