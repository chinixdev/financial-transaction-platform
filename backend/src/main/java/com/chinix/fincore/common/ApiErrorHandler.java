package com.chinix.fincore.common;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(
      Map.of(
        "error",
        e.getReason() == null ? "Request rejected" : e.getReason()
      )
    );
  }

  @ExceptionHandler(
    org.springframework.web.bind.MethodArgumentNotValidException.class
  )
  ResponseEntity<Map<String, String>> invalid() {
    return ResponseEntity.badRequest().body(
      Map.of("error", "Invalid request fields")
    );
  }

  @ExceptionHandler(
    org.springframework.dao.DataIntegrityViolationException.class
  )
  ResponseEntity<Map<String, String>> conflict() {
    return ResponseEntity.status(409).body(
      Map.of(
        "error",
        "Concurrent or duplicate request; retry with the same idempotency key"
      )
    );
  }
}
