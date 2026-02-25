package walshe.projectcolumbo.api.v1;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {SignalController.class, MarketPulseController.class})
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Parameter Value");
        problemDetail.setProperty("parameter", ex.getName());
        problemDetail.setProperty("value", ex.getValue());
        return problemDetail;
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ProblemDetail handleMissingServletRequestParameterException(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Missing Required Parameter");
        problemDetail.setProperty("parameter", ex.getParameterName());
        return problemDetail;
    }
}
