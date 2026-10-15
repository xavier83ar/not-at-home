package com.notathome.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors are RFC 9457 {@code ProblemDetail} JSON. Validation failures add an {@code errors}
 * property that maps each field name to its messages.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, List<String>> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors
          .computeIfAbsent(error.getField(), field -> new ArrayList<>())
          .add(error.getDefaultMessage());
    }
    return handleExceptionInternal(
        ex, validationProblem(ex.getBody(), errors), headers, status, request);
  }

  @ExceptionHandler(FieldErrorsException.class)
  protected ResponseEntity<Object> handleFieldErrors(FieldErrorsException ex, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    return handleExceptionInternal(
        ex,
        validationProblem(problem, ex.getErrors()),
        new HttpHeaders(),
        HttpStatus.BAD_REQUEST,
        request);
  }

  private static ProblemDetail validationProblem(
      ProblemDetail problem, Map<String, List<String>> errors) {
    problem.setDetail("Validation failed");
    problem.setProperty("errors", errors);
    return problem;
  }
}
