package riven.core.controller.storage

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import riven.core.enums.storage.StorageDomain
import riven.core.models.request.storage.BatchDeleteRequest
import riven.core.models.request.storage.ConfirmUploadRequest
import riven.core.models.request.storage.GenerateSignedUrlRequest
import riven.core.models.request.storage.PresignedUploadRequest
import riven.core.models.request.storage.UpdateMetadataRequest
import riven.core.models.response.storage.BatchDeleteResponse
import riven.core.models.response.storage.BatchUploadResponse
import riven.core.models.response.storage.FileListResponse
import riven.core.models.response.storage.PresignedUploadResponse
import riven.core.models.response.storage.SignedUrlResponse
import riven.core.models.response.storage.UploadFileResponse
import riven.core.models.storage.FileMetadata
import riven.core.service.storage.StorageService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/storage")
@Tag(name = "storage")
class StorageController(
    private val storageService: StorageService,
    private val objectMapper: ObjectMapper
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
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) metadata: String?
    ): ResponseEntity<UploadFileResponse> {
        val parsedMetadata = metadata?.let { objectMapper.readValue<Map<String, String>>(it) }
        val response = storageService.uploadFile(workspaceId, domain, file, parsedMetadata)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/workspace/{workspaceId}/presigned-upload")
    @Operation(summary = "Request a presigned upload URL for direct-to-provider upload")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Presigned URL generated or unsupported signal"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun requestPresignedUpload(
        @PathVariable workspaceId: UUID,
        @RequestBody request: PresignedUploadRequest
    ): ResponseEntity<PresignedUploadResponse> {
        val response = storageService.requestPresignedUpload(workspaceId, request.domain, request.contentType)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/workspace/{workspaceId}/presigned-upload/confirm")
    @Operation(summary = "Confirm a direct upload and persist file metadata")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "File confirmed and metadata persisted"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "File not found at storage key"),
        ApiResponse(responseCode = "415", description = "Content type not allowed")
    )
    fun confirmPresignedUpload(
        @PathVariable workspaceId: UUID,
        @RequestBody request: ConfirmUploadRequest
    ): ResponseEntity<UploadFileResponse> {
        val response = storageService.confirmPresignedUpload(workspaceId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/workspace/{workspaceId}/files/{fileId}/metadata")
    @Operation(summary = "Update custom metadata on a file")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Metadata updated"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "File not found")
    )
    fun updateMetadata(
        @PathVariable workspaceId: UUID,
        @PathVariable fileId: UUID,
        @RequestBody request: UpdateMetadataRequest
    ): ResponseEntity<FileMetadata> {
        val metadata = storageService.updateMetadata(workspaceId, fileId, request)
        return ResponseEntity.ok(metadata)
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

    @PostMapping("/workspace/{workspaceId}/batch-upload")
    @Operation(summary = "Upload multiple files in a single request")
    @ApiResponses(
        ApiResponse(responseCode = "207", description = "Batch processed with per-item results"),
        ApiResponse(responseCode = "400", description = "Exceeds 10 file limit or empty list"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun batchUpload(
        @PathVariable workspaceId: UUID,
        @RequestParam domain: StorageDomain,
        @RequestParam("files") files: List<MultipartFile>
    ): ResponseEntity<BatchUploadResponse> {
        val response = storageService.batchUpload(workspaceId, domain, files)
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response)
    }

    @PostMapping("/workspace/{workspaceId}/batch-delete")
    @Operation(summary = "Delete multiple files in a single request")
    @ApiResponses(
        ApiResponse(responseCode = "207", description = "Batch processed with per-item results"),
        ApiResponse(responseCode = "400", description = "Exceeds 50 file ID limit or empty list"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun batchDelete(
        @PathVariable workspaceId: UUID,
        @RequestBody request: BatchDeleteRequest
    ): ResponseEntity<BatchDeleteResponse> {
        val response = storageService.batchDelete(workspaceId, request)
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response)
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
            ContentDisposition.attachment().filename(filename, Charsets.UTF_8).build().toString()
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
