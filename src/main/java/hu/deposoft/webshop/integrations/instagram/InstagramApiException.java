package hu.deposoft.webshop.integrations.instagram;

public class InstagramApiException extends RuntimeException {
    public InstagramApiException(String message) { super(message); }
    public InstagramApiException(String message, Throwable cause) { super(message, cause); }
}
