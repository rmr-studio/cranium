# NoteApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createNote**](NoteApi.md#createnoteoperation) | **POST** /api/v1/notes/workspace/{workspaceId}/entity/{entityId} | Create a note for an entity |
| [**deleteNote**](NoteApi.md#deletenote) | **DELETE** /api/v1/notes/workspace/{workspaceId}/{noteId} | Delete a note |
| [**getNotesForEntity**](NoteApi.md#getnotesforentity) | **GET** /api/v1/notes/workspace/{workspaceId}/entity/{entityId} | List notes for an entity |
| [**updateNote**](NoteApi.md#updatenoteoperation) | **PUT** /api/v1/notes/workspace/{workspaceId}/{noteId} | Update a note |



## createNote

> Note createNote(workspaceId, entityId, createNoteRequest)

Create a note for an entity

### Example

```ts
import {
  Configuration,
  NoteApi,
} from '';
import type { CreateNoteOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NoteApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CreateNoteRequest
    createNoteRequest: ...,
  } satisfies CreateNoteOperationRequest;

  try {
    const data = await api.createNote(body);
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
| **entityId** | `string` |  | [Defaults to `undefined`] |
| **createNoteRequest** | [CreateNoteRequest](CreateNoteRequest.md) |  | |

### Return type

[**Note**](Note.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Note created |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteNote

> deleteNote(workspaceId, noteId)

Delete a note

### Example

```ts
import {
  Configuration,
  NoteApi,
} from '';
import type { DeleteNoteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NoteApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    noteId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteNoteRequest;

  try {
    const data = await api.deleteNote(body);
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
| **noteId** | `string` |  | [Defaults to `undefined`] |

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
| **204** | Note deleted |  -  |
| **404** | Note not found |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getNotesForEntity

> Array&lt;Note&gt; getNotesForEntity(workspaceId, entityId, search)

List notes for an entity

### Example

```ts
import {
  Configuration,
  NoteApi,
} from '';
import type { GetNotesForEntityRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NoteApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string (optional)
    search: search_example,
  } satisfies GetNotesForEntityRequest;

  try {
    const data = await api.getNotesForEntity(body);
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
| **entityId** | `string` |  | [Defaults to `undefined`] |
| **search** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Array&lt;Note&gt;**](Note.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Notes retrieved |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateNote

> Note updateNote(workspaceId, noteId, updateNoteRequest)

Update a note

### Example

```ts
import {
  Configuration,
  NoteApi,
} from '';
import type { UpdateNoteOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NoteApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    noteId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // UpdateNoteRequest
    updateNoteRequest: ...,
  } satisfies UpdateNoteOperationRequest;

  try {
    const data = await api.updateNote(body);
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
| **noteId** | `string` |  | [Defaults to `undefined`] |
| **updateNoteRequest** | [UpdateNoteRequest](UpdateNoteRequest.md) |  | |

### Return type

[**Note**](Note.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Note updated |  -  |
| **404** | Note not found |  -  |
| **403** | Access denied |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

