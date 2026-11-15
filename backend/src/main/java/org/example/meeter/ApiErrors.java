package org.example.meeter;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.stream.Collectors;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(MethodArgumentNotValidException.class) public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
 return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).collect(Collectors.joining("; ")));
 }
 @ExceptionHandler(ConstraintViolationException.class) public ProblemDetail invalidQuery(ConstraintViolationException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Nieprawidłowe parametry zapytania."); }
 @ExceptionHandler(DataIntegrityViolationException.class) public ProblemDetail conflict(DataIntegrityViolationException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Nie można zapisać danych ze względu na ograniczenia bazy."); }
}
