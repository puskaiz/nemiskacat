package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.customer.CustomerRegistrationService;
import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.domain.order.OrderRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

/**
 * Customer account hub on the WP-preserved slug (/fiokom). Anonymous visitors see
 * the login + register forms; authenticated customers see their profile and order
 * history. Login/logout themselves are handled by Spring Security
 * (/fiokom/belepes, /fiokom/kilepes). Session-dependent → no-store.
 */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final CustomerRegistrationService registration;
    private final CustomerRepository customers;
    private final OrderRepository orders;

    public record RegisterForm(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "A jelszó legalább 8 karakter legyen") String password,
            String firstName,
            String lastName) {
    }

    @GetMapping({"/fiokom", "/fiokom/"})
    public String account(Principal principal, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (principal == null) {
            return "account-login";
        }
        Customer customer = customers.findByEmailIgnoreCase(principal.getName()).orElseThrow();
        model.addAttribute("customer", customer);
        model.addAttribute("orders", orders.findByEmailWithItems(customer.getEmail()));
        return "account";
    }

    @PostMapping("/fiokom/regisztracio")
    public String register(@ModelAttribute @jakarta.validation.Valid RegisterForm form,
                           BindingResult binding, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (binding.hasErrors()) {
            model.addAttribute("registerError", "Érvénytelen adatok.");
            return "account-login";
        }
        try {
            registration.register(form.email(), form.password(), form.firstName(), form.lastName());
        } catch (CustomerRegistrationService.EmailTakenException e) {
            model.addAttribute("registerError", "Ezzel az e-mail címmel már van fiók.");
            return "account-login";
        }
        return "redirect:/fiokom?regisztralt";
    }
}
