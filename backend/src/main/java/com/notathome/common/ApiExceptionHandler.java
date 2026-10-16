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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

  /**
   * Thrown by the servlet container before the upload endpoint's own (lower) size check runs, for a
   * request so large it isn't worth reading at all. Rendered the same way as that check's error.
   */
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    Map<String, List<String>> errors = Map.of("file", List.of("must be 5 MB or smaller"));
    return handleExceptionInternal(
        ex, validationProblem(problem, errors), headers, HttpStatus.BAD_REQUEST, request);
  }

  private static ProblemDetail validationProblem(
      ProblemDetail problem, Map<String, List<String>> errors) {
    problem.setDetail("Validation failed");
    problem.setProperty("errors", errors);
    return problem;
  }
}
