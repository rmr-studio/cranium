# StorageApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**batchDelete**](StorageApi.md#batchdeleteoperation) | **POST** /api/v1/storage/workspace/{workspaceId}/batch-delete | Delete multiple files in a single request |
| [**batchUpload**](StorageApi.md#batchupload) | **POST** /api/v1/storage/workspace/{workspaceId}/batch-upload | Upload multiple files in a single request |
| [**confirmPresignedUpload**](StorageApi.md#confirmpresignedupload) | **POST** /api/v1/storage/workspace/{workspaceId}/presigned-upload/confirm | Confirm a direct upload and persist file metadata |
| [**deleteFile**](StorageApi.md#deletefile) | **DELETE** /api/v1/storage/workspace/{workspaceId}/files/{fileId} | Delete a file |
| [**downloadFile**](StorageApi.md#downloadfile) | **GET** /api/v1/storage/download/{token} | Download a file using a signed URL token |
| [**generateSignedUrl**](StorageApi.md#generatesignedurloperation) | **POST** /api/v1/storage/workspace/{workspaceId}/files/{fileId}/signed-url | Generate a signed download URL for a file |
| [**getFile**](StorageApi.md#getfile) | **GET** /api/v1/storage/workspace/{workspaceId}/files/{fileId} | Get file metadata |
| [**listFiles**](StorageApi.md#listfiles) | **GET** /api/v1/storage/workspace/{workspaceId}/files | List files in a workspace |
| [**requestPresignedUpload**](StorageApi.md#requestpresignedupload) | **POST** /api/v1/storage/workspace/{workspaceId}/presigned-upload | Request a presigned upload URL for direct-to-provider upload |
| [**updateMetadata**](StorageApi.md#updatemetadataoperation) | **PATCH** /api/v1/storage/workspace/{workspaceId}/files/{fileId}/metadata | Update custom metadata on a file |
| [**uploadFile**](StorageApi.md#uploadfileoperation) | **POST** /api/v1/storage/workspace/{workspaceId}/upload | Upload a file to storage |



## batchDelete

> BatchDeleteResponse batchDelete(workspaceId, batchDeleteRequest)

Delete multiple files in a single request

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { BatchDeleteOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // BatchDeleteRequest
    batchDeleteRequest: ...,
  } satisfies BatchDeleteOperationRequest;

  try {
    const data = await api.batchDelete(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **batchDeleteRequest** | [BatchDeleteRequest](BatchDeleteRequest.md) |  | |

### Return type

[**BatchDeleteResponse**](BatchDeleteResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **207** | Batch processed with per-item results |  -  |
| **400** | Exceeds 50 file ID limit or empty list |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## batchUpload

> BatchUploadResponse batchUpload(workspaceId, domain, files)

Upload multiple files in a single request

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { BatchUploadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // StorageDomain
    domain: ...,
    // Array<Blob>
    files: ...,
  } satisfies BatchUploadRequest;

  try {
    const data = await api.batchUpload(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **domain** | `StorageDomain` |  | [Defaults to `undefined`] [Enum: AVATAR] |
| **files** | `Array<Blob>` |  | |

### Return type

[**BatchUploadResponse**](BatchUploadResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **207** | Batch processed with per-item results |  -  |
| **400** | Exceeds 10 file limit or empty list |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## confirmPresignedUpload

> UploadFileResponse confirmPresignedUpload(workspaceId, confirmUploadRequest)

Confirm a direct upload and persist file metadata

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { ConfirmPresignedUploadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ConfirmUploadRequest
    confirmUploadRequest: ...,
  } satisfies ConfirmPresignedUploadRequest;

  try {
    const data = await api.confirmPresignedUpload(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **confirmUploadRequest** | [ConfirmUploadRequest](ConfirmUploadRequest.md) |  | |

### Return type

[**UploadFileResponse**](UploadFileResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | File confirmed and metadata persisted |  -  |
| **401** | Unauthorized access |  -  |
| **404** | File not found at storage key |  -  |
| **415** | Content type not allowed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteFile

> deleteFile(workspaceId, fileId)

Delete a file

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { DeleteFileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    fileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteFileRequest;

  try {
    const data = await api.deleteFile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **fileId** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **204** | File deleted |  -  |
| **401** | Unauthorized access |  -  |
| **404** | File not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## downloadFile

> object downloadFile(token, download)

Download a file using a signed URL token

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { DownloadFileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    token: token_example,
    // boolean (optional)
    download: true,
  } satisfies DownloadFileRequest;

  try {
    const data = await api.downloadFile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **token** | `string` |  | [Defaults to `undefined`] |
| **download** | `boolean` |  | [Optional] [Defaults to `false`] |

### Return type

**object**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | File content streamed |  -  |
| **403** | Token expired or invalid |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## generateSignedUrl

> SignedUrlResponse generateSignedUrl(workspaceId, fileId, generateSignedUrlRequest)

Generate a signed download URL for a file

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { GenerateSignedUrlOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    fileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // GenerateSignedUrlRequest (optional)
    generateSignedUrlRequest: ...,
  } satisfies GenerateSignedUrlOperationRequest;

  try {
    const data = await api.generateSignedUrl(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **fileId** | `string` |  | [Defaults to `undefined`] |
| **generateSignedUrlRequest** | [GenerateSignedUrlRequest](GenerateSignedUrlRequest.md) |  | [Optional] |

### Return type

[**SignedUrlResponse**](SignedUrlResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Signed URL generated |  -  |
| **401** | Unauthorized access |  -  |
| **404** | File not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getFile

> FileMetadata getFile(workspaceId, fileId)

Get file metadata

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { GetFileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    fileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetFileRequest;

  try {
    const data = await api.getFile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **fileId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**FileMetadata**](FileMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | File metadata retrieved |  -  |
| **401** | Unauthorized access |  -  |
| **404** | File not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listFiles

> FileListResponse listFiles(workspaceId, domain)

List files in a workspace

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { ListFilesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // StorageDomain (optional)
    domain: ...,
  } satisfies ListFilesRequest;

  try {
    const data = await api.listFiles(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **domain** | `StorageDomain` |  | [Optional] [Defaults to `undefined`] [Enum: AVATAR] |

### Return type

[**FileListResponse**](FileListResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Files listed successfully |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## requestPresignedUpload

> PresignedUploadResponse requestPresignedUpload(workspaceId, presignedUploadRequest)

Request a presigned upload URL for direct-to-provider upload

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { RequestPresignedUploadRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // PresignedUploadRequest
    presignedUploadRequest: ...,
  } satisfies RequestPresignedUploadRequest;

  try {
    const data = await api.requestPresignedUpload(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **presignedUploadRequest** | [PresignedUploadRequest](PresignedUploadRequest.md) |  | |

### Return type

[**PresignedUploadResponse**](PresignedUploadResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Presigned URL generated or unsupported signal |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateMetadata

> FileMetadata updateMetadata(workspaceId, fileId, updateMetadataRequest)

Update custom metadata on a file

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { UpdateMetadataOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    fileId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // UpdateMetadataRequest
    updateMetadataRequest: ...,
  } satisfies UpdateMetadataOperationRequest;

  try {
    const data = await api.updateMetadata(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **fileId** | `string` |  | [Defaults to `undefined`] |
| **updateMetadataRequest** | [UpdateMetadataRequest](UpdateMetadataRequest.md) |  | |

### Return type

[**FileMetadata**](FileMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Metadata updated |  -  |
| **401** | Unauthorized access |  -  |
| **404** | File not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## uploadFile

> UploadFileResponse uploadFile(workspaceId, domain, metadata, uploadFileRequest)

Upload a file to storage

### Example

```ts
import {
  Configuration,
  StorageApi,
} from '';
import type { UploadFileOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new StorageApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // StorageDomain
    domain: ...,
    // string (optional)
    metadata: metadata_example,
    // UploadFileRequest (optional)
    uploadFileRequest: ...,
  } satisfies UploadFileOperationRequest;

  try {
    const data = await api.uploadFile(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **workspaceId** | `string` |  | [Defaults to `undefined`] |
| **domain** | `StorageDomain` |  | [Defaults to `undefined`] [Enum: AVATAR] |
| **metadata** | `string` |  | [Optional] [Defaults to `undefined`] |
| **uploadFileRequest** | [UploadFileRequest](UploadFileRequest.md) |  | [Optional] |

### Return type

[**UploadFileResponse**](UploadFileResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | File uploaded successfully |  -  |
| **401** | Unauthorized access |  -  |
| **413** | File size exceeds limit |  -  |
| **415** | Content type not allowed |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

