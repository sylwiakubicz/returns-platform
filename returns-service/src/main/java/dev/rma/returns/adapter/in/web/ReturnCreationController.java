package dev.rma.returns.adapter.in.web;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.rma.returns.application.IdempotentReturnCreationService;

@RestController
@RequestMapping("/api/v1/returns")
public class ReturnCreationController {
    private final IdempotentReturnCreationService service;

    public ReturnCreationController(IdempotentReturnCreationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestHeader("Idempotency-Key") String key, @RequestBody byte[] body) {
        var response = service.create(key, body);
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .location(URI.create(response.location()))
                .body(response.body());
    }
}
