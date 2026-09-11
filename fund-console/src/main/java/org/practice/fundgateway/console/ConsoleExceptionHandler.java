package org.practice.fundgateway.console;

import org.practice.fundgateway.guardian.workflow.DiagnosticWorkflowException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 把控制台所有业务异常转换为一致的页面错误响应。 */
@RestControllerAdvice
public class ConsoleExceptionHandler {

    /** 将输入、状态和审批错误返回为可展示的四百响应。 */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            DiagnosticWorkflowException.class})
    public ResponseEntity<ConsoleError> businessError(RuntimeException exception) {
        return ResponseEntity.badRequest().body(new ConsoleError("CONSOLE_REQUEST_FAILED", exception.getMessage()));
    }

    /** 页面统一错误结构。 */
    public record ConsoleError(String code, String message) {
    }
}
