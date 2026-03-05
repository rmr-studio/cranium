package riven.core.controller.storage

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import riven.core.enums.storage.StorageDomain
import riven.core.models.request.storage.GenerateSignedUrlRequest
import riven.core.models.response.storage.FileListResponse
import riven.core.models.response.storage.SignedUrlResponse
import riven.core.models.response.storage.UploadFileResponse
import riven.core.models.storage.FileMetadata
import riven.core.service.storage.StorageService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/storage")
@Tag(name = "storage")
class StorageController(
    private val storageService: StorageService
) {

    @PostMapping("/workspace/{workspaceId}/upload")
    @Operation(summary = "Upload a file to storage")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "File uploaded successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "413", description = "File size exceeds limit"),
        ApiResponse(responseCode = "415", description = "Content type not allowed")
    )
    fun uploadFile(
        @PathVariable workspaceId: UUID,
        @RequestParam domain: StorageDomain,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<UploadFileResponse> {
        val response = storageService.uploadFile(workspaceId, domain, file)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/workspace/{workspaceId}/files")
    @Operation(summary = "List files in a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Files listed successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun listFiles(
        @PathVariable workspaceId: UUID,
        @RequestParam(required = false) domain: StorageDomain?
    ): ResponseEntity<FileListResponse> {
        val response = storageService.listFiles(workspaceId, domain)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/workspace/{workspaceId}/files/{fileId}")
    @Operation(summary = "Get file metadata")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "File metadata retrieved"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "File not found")
    )
    fun getFile(
        @PathVariable workspaceId: UUID,
        @PathVariable fileId: UUID
    ): ResponseEntity<FileMetadata> {
        val metadata = storageService.getFile(workspaceId, fileId)
        return ResponseEntity.ok(metadata)
    }

    @PostMapping("/workspace/{workspaceId}/files/{fileId}/signed-url")
    @Operation(summary = "Generate a signed download URL for a file")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Signed URL generated"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "File not found")
    )
    fun generateSignedUrl(
        @PathVariable workspaceId: UUID,
        @PathVariable fileId: UUID,
        @RequestBody(required = false) request: GenerateSignedUrlRequest?
    ): ResponseEntity<SignedUrlResponse> {
        val response = storageService.generateSignedUrl(workspaceId, fileId, request?.expiresInSeconds)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/workspace/{workspaceId}/files/{fileId}")
    @Operation(summary = "Delete a file")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "File deleted"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "File not found")
    )
    fun deleteFile(
        @PathVariable workspaceId: UUID,
        @PathVariable fileId: UUID
    ): ResponseEntity<Void> {
        storageService.deleteFile(workspaceId, fileId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/download/{token}")
    @Operation(summary = "Download a file using a signed URL token")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "File content streamed"),
        ApiResponse(responseCode = "403", description = "Token expired or invalid")
    )
    fun downloadFile(
        @PathVariable token: String,
        @RequestParam(required = false, defaultValue = "false") download: Boolean
    ): ResponseEntity<StreamingResponseBody> {
        val result = storageService.downloadFile(token)

        val disposition = if (download) {
            val filename = result.originalFilename ?: "download"
            "attachment; filename=\"$filename\""
        } else {
            "inline"
        }

        val body = StreamingResponseBody { outputStream ->
            result.content.use { input ->
                input.copyTo(outputStream)
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, result.contentType)
            .header(HttpHeaders.CONTENT_LENGTH, result.contentLength.toString())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .body(body)
    }
}
