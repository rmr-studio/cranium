# AvatarsApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getUserAvatar**](AvatarsApi.md#getuseravatar) | **GET** /api/v1/avatars/user/{userId} | Redirect to a signed URL for a user\&#39;s avatar |
| [**getWorkspaceAvatar**](AvatarsApi.md#getworkspaceavatar) | **GET** /api/v1/avatars/workspace/{workspaceId} | Redirect to a signed URL for a workspace\&#39;s avatar |



## getUserAvatar

> getUserAvatar(userId)

Redirect to a signed URL for a user\&#39;s avatar

### Example

```ts
import {
  Configuration,
  AvatarsApi,
} from '';
import type { GetUserAvatarRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AvatarsApi(config);

  const body = {
    // string
    userId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetUserAvatarRequest;

  try {
    const data = await api.getUserAvatar(body);
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
| **userId** | `string` |  | [Defaults to `undefined`] |

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
| **302** | Redirect to signed avatar URL |  -  |
| **404** | User has no avatar |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getWorkspaceAvatar

> getWorkspaceAvatar(workspaceId)

Redirect to a signed URL for a workspace\&#39;s avatar

### Example

```ts
import {
  Configuration,
  AvatarsApi,
} from '';
import type { GetWorkspaceAvatarRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AvatarsApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetWorkspaceAvatarRequest;

  try {
    const data = await api.getWorkspaceAvatar(body);
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
| **302** | Redirect to signed avatar URL |  -  |
| **404** | Workspace has no avatar |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

