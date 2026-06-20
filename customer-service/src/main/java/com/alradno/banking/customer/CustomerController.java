package com.alradno.banking.customer;

import com.alradno.banking.common.http.Correlation;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerRepository customers;

    public CustomerController(CustomerRepository customers) {
        this.customers = customers;
    }

    @GetMapping("/me")
    public CustomerResponse me(@RequestHeader(Correlation.USER_SUBJECT) String subject) {
        return customers.findBySubject(subject)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customers.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<CustomerResponse> list() {
        return customers.findAll().stream().map(CustomerResponse::from).toList();
    }

    public record CustomerResponse(UUID id, String subject, String fullName, CustomerStatus status, RiskLevel riskLevel) {
        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                    customer.getId(),
                    customer.getSubject(),
                    customer.getFullName(),
                    customer.getStatus(),
                    customer.getRiskLevel());
        }
    }
}
