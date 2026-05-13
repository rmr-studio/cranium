package cranium.core.service.workflow.engine.error

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import cranium.core.enums.workflow.WorkflowErrorType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.exceptions.SchemaValidationException
import java.nio.charset.Charset

/**
 * Unit tests for WorkflowErrorClassifier.
 *
 * Tests the error classification logic directly without mocking,
 * since WorkflowErrorClassifier is a stateless utility object.
 */
class WorkflowErrorClassifierTest {

    @Nested
    @DisplayName("HTTP Error Classification")
    inner class HttpErrorClassification {

        @Test
        @DisplayName("HTTP 400 Bad Request should be classified as HTTP_CLIENT_ERROR (non-retryable)")
        fun `HTTP 400 should be non-retryable HTTP_CLIENT_ERROR`() {
            val exception = WebClientResponseException.create(
                400, "Bad Request", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertFalse(errorType.retryable, "HTTP 4xx errors should not be retryable")
        }

        @Test
        @DisplayName("HTTP 401 Unauthorized should be classified as HTTP_CLIENT_ERROR (non-retryable)")
        fun `HTTP 401 should be non-retryable HTTP_CLIENT_ERROR`() {
            val exception = WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 403 Forbidden should be classified as HTTP_CLIENT_ERROR (non-retryable)")
        fun `HTTP 403 should be non-retryable HTTP_CLIENT_ERROR`() {
            val exception = WebClientResponseException.create(
                403, "Forbidden", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 404 Not Found should be classified as HTTP_CLIENT_ERROR (non-retryable)")
        fun `HTTP 404 should be non-retryable HTTP_CLIENT_ERROR`() {
            val exception = WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 422 Unprocessable Entity should be classified as HTTP_CLIENT_ERROR (non-retryable)")
        fun `HTTP 422 should be non-retryable HTTP_CLIENT_ERROR`() {
            val exception = WebClientResponseException.create(
                422, "Unprocessable Entity", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 500 Internal Server Error should be classified as HTTP_SERVER_ERROR (retryable)")
        fun `HTTP 500 should be retryable HTTP_SERVER_ERROR`() {
            val exception = WebClientResponseException.create(
                500, "Internal Server Error", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_SERVER_ERROR, errorType)
            assertTrue(errorType.retryable, "HTTP 5xx errors should be retryable")
        }

        @Test
        @DisplayName("HTTP 502 Bad Gateway should be classified as HTTP_SERVER_ERROR (retryable)")
        fun `HTTP 502 should be retryable HTTP_SERVER_ERROR`() {
            val exception = WebClientResponseException.create(
                502, "Bad Gateway", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_SERVER_ERROR, errorType)
            assertTrue(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 503 Service Unavailable should be classified as HTTP_SERVER_ERROR (retryable)")
        fun `HTTP 503 should be retryable HTTP_SERVER_ERROR`() {
            val exception = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_SERVER_ERROR, errorType)
            assertTrue(errorType.retryable)
        }

        @Test
        @DisplayName("HTTP 504 Gateway Timeout should be classified as HTTP_SERVER_ERROR (retryable)")
        fun `HTTP 504 should be retryable HTTP_SERVER_ERROR`() {
            val exception = WebClientResponseException.create(
                504, "Gateway Timeout", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.HTTP_SERVER_ERROR, errorType)
            assertTrue(errorType.retryable)
        }
    }

    @Nested
    @DisplayName("Validation Error Classification")
    inner class ValidationErrorClassification {

        @Test
        @DisplayName("IllegalArgumentException should be classified as VALIDATION_ERROR (non-retryable)")
        fun `IllegalArgumentException should be non-retryable VALIDATION_ERROR`() {
            val exception = IllegalArgumentException("Invalid input parameter")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.VALIDATION_ERROR, errorType)
            assertFalse(errorType.retryable, "Validation errors should not be retryable")
        }

        @Test
        @DisplayName("SchemaValidationException should be classified as VALIDATION_ERROR (non-retryable)")
        fun `SchemaValidationException should be non-retryable VALIDATION_ERROR`() {
            val exception = SchemaValidationException(listOf("Field 'email' is required"))

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.VALIDATION_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("IllegalArgumentException in TRIGGER node should still be VALIDATION_ERROR")
        fun `IllegalArgumentException in TRIGGER node should be VALIDATION_ERROR`() {
            val exception = IllegalArgumentException("Invalid trigger configuration")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.TRIGGER)

            assertEquals(WorkflowErrorType.VALIDATION_ERROR, errorType)
            assertFalse(errorType.retryable)
        }
    }

    @Nested
    @DisplayName("Security Error Classification")
    inner class SecurityErrorClassification {

        @Test
        @DisplayName("SecurityException should be classified as SECURITY_ERROR (non-retryable)")
        fun `SecurityException should be non-retryable SECURITY_ERROR`() {
            val exception = SecurityException("Access denied to resource")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.SECURITY_ERROR, errorType)
            assertFalse(errorType.retryable, "Security errors should not be retryable")
        }

        @Test
        @DisplayName("SecurityException in TRIGGER node should be SECURITY_ERROR")
        fun `SecurityException in TRIGGER should be SECURITY_ERROR`() {
            val exception = SecurityException("Unauthorized webhook call")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.TRIGGER)

            assertEquals(WorkflowErrorType.SECURITY_ERROR, errorType)
            assertFalse(errorType.retryable)
        }
    }

    @Nested
    @DisplayName("Control Flow Error Classification")
    inner class ControlFlowErrorClassification {

        @Test
        @DisplayName("Any exception in CONTROL_FLOW node should be classified as CONTROL_FLOW_ERROR (non-retryable)")
        fun `CONTROL_FLOW node errors should be non-retryable CONTROL_FLOW_ERROR`() {
            val exception = RuntimeException("Condition evaluation failed")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.CONTROL_FLOW)

            assertEquals(WorkflowErrorType.CONTROL_FLOW_ERROR, errorType)
            assertFalse(errorType.retryable, "Control flow errors should not be retryable")
        }

        @Test
        @DisplayName("NullPointerException in CONTROL_FLOW node should be CONTROL_FLOW_ERROR")
        fun `NPE in CONTROL_FLOW should be CONTROL_FLOW_ERROR`() {
            val exception = NullPointerException("Null value in condition")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.CONTROL_FLOW)

            assertEquals(WorkflowErrorType.CONTROL_FLOW_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("ArithmeticException in CONTROL_FLOW node should be CONTROL_FLOW_ERROR")
        fun `ArithmeticException in CONTROL_FLOW should be CONTROL_FLOW_ERROR`() {
            val exception = ArithmeticException("Division by zero in condition")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.CONTROL_FLOW)

            assertEquals(WorkflowErrorType.CONTROL_FLOW_ERROR, errorType)
            assertFalse(errorType.retryable)
        }

        @Test
        @DisplayName("IllegalArgumentException in CONTROL_FLOW node should be CONTROL_FLOW_ERROR not VALIDATION_ERROR")
        fun `IllegalArgumentException in CONTROL_FLOW takes precedence over VALIDATION_ERROR`() {
            // CONTROL_FLOW node type should take precedence over exception type classification
            val exception = IllegalArgumentException("Invalid condition syntax")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.CONTROL_FLOW)

            // CONTROL_FLOW node context takes precedence over exception type
            assertEquals(WorkflowErrorType.CONTROL_FLOW_ERROR, errorType)
            assertFalse(errorType.retryable, "Control flow errors should not be retryable")
        }
    }

    @Nested
    @DisplayName("Generic Error Classification")
    inner class GenericErrorClassification {

        @Test
        @DisplayName("Generic RuntimeException in ACTION node should be classified as EXECUTION_ERROR (retryable)")
        fun `RuntimeException in ACTION should be retryable EXECUTION_ERROR`() {
            val exception = RuntimeException("Something unexpected happened")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(errorType.retryable, "Generic execution errors should be retryable")
        }

        @Test
        @DisplayName("Generic Exception in ACTION node should be classified as EXECUTION_ERROR (retryable)")
        fun `Exception in ACTION should be retryable EXECUTION_ERROR`() {
            val exception = Exception("Generic error")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(errorType.retryable)
        }

        @Test
        @DisplayName("OutOfMemoryError wrapped in exception should be EXECUTION_ERROR (retryable)")
        fun `wrapped OutOfMemoryError should be EXECUTION_ERROR`() {
            val exception = RuntimeException("Memory issue", OutOfMemoryError("Heap space"))

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(errorType.retryable)
        }

        @Test
        @DisplayName("IOException wrapped as RuntimeException should be EXECUTION_ERROR (retryable)")
        fun `wrapped IOException should be EXECUTION_ERROR`() {
            val exception = RuntimeException("IO failed", java.io.IOException("Connection reset"))

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.ACTION)

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(errorType.retryable)
        }

        @Test
        @DisplayName("RuntimeException in TRIGGER node should be EXECUTION_ERROR (retryable)")
        fun `RuntimeException in TRIGGER should be EXECUTION_ERROR`() {
            val exception = RuntimeException("Trigger failed unexpectedly")

            val errorType = WorkflowErrorClassifier.classifyError(exception, WorkflowNodeType.TRIGGER)

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(errorType.retryable)
        }
    }

    @Nested
    @DisplayName("classifyErrorWithMessage")
    inner class ClassifyErrorWithMessage {

        @Test
        @DisplayName("Should return HTTP status code in message for HTTP 4xx errors")
        fun `should include HTTP status in message for HTTP 4xx errors`() {
            val exception = WebClientResponseException.create(
                404, "Not Found", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.ACTION
            )

            assertEquals(WorkflowErrorType.HTTP_CLIENT_ERROR, errorType)
            assertTrue(message.contains("404"), "Message should contain HTTP status code")
            assertTrue(message.startsWith("HTTP "), "Message should start with 'HTTP '")
        }

        @Test
        @DisplayName("Should return HTTP status code in message for HTTP 5xx errors")
        fun `should include HTTP status in message for HTTP 5xx errors`() {
            val exception = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), Charset.defaultCharset()
            )

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.ACTION
            )

            assertEquals(WorkflowErrorType.HTTP_SERVER_ERROR, errorType)
            assertTrue(message.contains("503"), "Message should contain HTTP status code")
        }

        @Test
        @DisplayName("Should return 'Validation failed' prefix for validation errors")
        fun `should prefix validation error messages`() {
            val exception = IllegalArgumentException("Email format is invalid")

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.ACTION
            )

            assertEquals(WorkflowErrorType.VALIDATION_ERROR, errorType)
            assertTrue(message.startsWith("Validation failed:"), "Message should start with 'Validation failed:'")
            assertTrue(message.contains("Email format is invalid"), "Message should contain original error message")
        }

        @Test
        @DisplayName("Should return 'Security error' prefix for security errors")
        fun `should prefix security error messages`() {
            val exception = SecurityException("Insufficient permissions")

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.ACTION
            )

            assertEquals(WorkflowErrorType.SECURITY_ERROR, errorType)
            assertTrue(message.startsWith("Security error:"), "Message should start with 'Security error:'")
            assertTrue(message.contains("Insufficient permissions"), "Message should contain original error message")
        }

        @Test
        @DisplayName("Should return 'Control flow failed' prefix for control flow errors")
        fun `should prefix control flow error messages`() {
            val exception = RuntimeException("Condition returned null")

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.CONTROL_FLOW
            )

            assertEquals(WorkflowErrorType.CONTROL_FLOW_ERROR, errorType)
            assertTrue(message.startsWith("Control flow failed:"), "Message should start with 'Control flow failed:'")
            assertTrue(message.contains("Condition returned null"), "Message should contain original error message")
        }

        @Test
        @DisplayName("Should return 'Execution error' prefix for generic errors")
        fun `should prefix generic error messages`() {
            val exception = RuntimeException("Unknown failure")

            val (errorType, message) = WorkflowErrorClassifier.classifyErrorWithMessage(
                exception, WorkflowNodeType.ACTION
            )

            assertEquals(WorkflowErrorType.EXECUTION_ERROR, errorType)
            assertTrue(message.startsWith("Execution error:"), "Message should start with 'Execution error:'")
            assertTrue(message.contains("Unknown failure"), "Message should contain original error message")
        }
    }
}
