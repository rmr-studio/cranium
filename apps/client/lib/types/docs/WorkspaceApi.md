# WorkspaceApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**acceptInvite**](WorkspaceApi.md#acceptinvite) | **POST** /api/v1/workspace/invite/accept/{inviteToken} |  |
| [**deleteWorkspace**](WorkspaceApi.md#deleteworkspace) | **DELETE** /api/v1/workspace/{workspaceId} |  |
| [**getUserInvites**](WorkspaceApi.md#getuserinvites) | **GET** /api/v1/workspace/invite/user |  |
| [**getWorkspace**](WorkspaceApi.md#getworkspace) | **GET** /api/v1/workspace/{workspaceId} |  |
| [**getWorkspaceInvites**](WorkspaceApi.md#getworkspaceinvites) | **GET** /api/v1/workspace/invite/workspace/{workspaceId} |  |
| [**installTemplate**](WorkspaceApi.md#installtemplate) | **POST** /api/v1/workspace/{workspaceId}/install-template | Install or reinstall the lifecycle template for a workspace. Idempotent — returns early if already installed. |
| [**inviteToWorkspace**](WorkspaceApi.md#invitetoworkspace) | **POST** /api/v1/workspace/invite/workspace/{workspaceId}/email/{email}/role/{role} |  |
| [**rejectInvite**](WorkspaceApi.md#rejectinvite) | **POST** /api/v1/workspace/invite/reject/{inviteToken} |  |
| [**removeMemberFromWorkspace**](WorkspaceApi.md#removememberfromworkspace) | **DELETE** /api/v1/workspace/{workspaceId}/member/{memberId} |  |
| [**revokeInvite**](WorkspaceApi.md#revokeinvite) | **DELETE** /api/v1/workspace/invite/workspace/{workspaceId}/invitation/{id} |  |
| [**saveWorkspace**](WorkspaceApi.md#saveworkspaceoperation) | **POST** /api/v1/workspace/ |  |
| [**updateMemberRole**](WorkspaceApi.md#updatememberrole) | **PUT** /api/v1/workspace/{workspaceId}/member/{memberId}/role/{role} |  |



## acceptInvite

> acceptInvite(inviteToken)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { AcceptInviteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    inviteToken: inviteToken_example,
  } satisfies AcceptInviteRequest;

  try {
    const data = await api.acceptInvite(body);
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
| **inviteToken** | `string` |  | [Defaults to `undefined`] |

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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteWorkspace

> deleteWorkspace(workspaceId)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { DeleteWorkspaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteWorkspaceRequest;

  try {
    const data = await api.deleteWorkspace(body);
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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getUserInvites

> Array&lt;WorkspaceInvite&gt; getUserInvites()



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { GetUserInvitesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  try {
    const data = await api.getUserInvites();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**Array&lt;WorkspaceInvite&gt;**](WorkspaceInvite.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getWorkspace

> Workspace getWorkspace(workspaceId, includeMetadata)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { GetWorkspaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // boolean (optional)
    includeMetadata: true,
  } satisfies GetWorkspaceRequest;

  try {
    const data = await api.getWorkspace(body);
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
| **includeMetadata** | `boolean` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Workspace**](Workspace.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getWorkspaceInvites

> Array&lt;WorkspaceInvite&gt; getWorkspaceInvites(workspaceId)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { GetWorkspaceInvitesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetWorkspaceInvitesRequest;

  try {
    const data = await api.getWorkspaceInvites(body);
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

[**Array&lt;WorkspaceInvite&gt;**](WorkspaceInvite.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## installTemplate

> TemplateInstallationResponse installTemplate(workspaceId, businessType)

Install or reinstall the lifecycle template for a workspace. Idempotent — returns early if already installed.

### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { InstallTemplateRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // BusinessType
    businessType: ...,
  } satisfies InstallTemplateRequest;

  try {
    const data = await api.installTemplate(body);
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
| **businessType** | `BusinessType` |  | [Defaults to `undefined`] [Enum: DTC_ECOMMERCE, B2C_SAAS] |

### Return type

[**TemplateInstallationResponse**](TemplateInstallationResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Template installed or already present |  -  |
| **404** | Template not found in catalog |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## inviteToWorkspace

> WorkspaceInvite inviteToWorkspace(workspaceId, email, role)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { InviteToWorkspaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    email: email_example,
    // WorkspaceRoles
    role: ...,
  } satisfies InviteToWorkspaceRequest;

  try {
    const data = await api.inviteToWorkspace(body);
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
| **email** | `string` |  | [Defaults to `undefined`] |
| **role** | `WorkspaceRoles` |  | [Defaults to `undefined`] [Enum: OWNER, ADMIN, MEMBER] |

### Return type

[**WorkspaceInvite**](WorkspaceInvite.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## rejectInvite

> rejectInvite(inviteToken)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { RejectInviteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    inviteToken: inviteToken_example,
  } satisfies RejectInviteRequest;

  try {
    const data = await api.rejectInvite(body);
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
| **inviteToken** | `string` |  | [Defaults to `undefined`] |

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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## removeMemberFromWorkspace

> removeMemberFromWorkspace(workspaceId, memberId)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { RemoveMemberFromWorkspaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    memberId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RemoveMemberFromWorkspaceRequest;

  try {
    const data = await api.removeMemberFromWorkspace(body);
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
| **memberId** | `string` |  | [Defaults to `undefined`] |

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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## revokeInvite

> revokeInvite(workspaceId, id)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { RevokeInviteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies RevokeInviteRequest;

  try {
    const data = await api.revokeInvite(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

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
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## saveWorkspace

> Workspace saveWorkspace(workspace, file)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { SaveWorkspaceOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // SaveWorkspaceRequest
    workspace: ...,
    // Blob (optional)
    file: BINARY_DATA_HERE,
  } satisfies SaveWorkspaceOperationRequest;

  try {
    const data = await api.saveWorkspace(body);
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
| **workspace** | [SaveWorkspaceRequest](SaveWorkspaceRequest.md) |  | [Defaults to `undefined`] |
| **file** | `Blob` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Workspace**](Workspace.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateMemberRole

> WorkspaceMember updateMemberRole(workspaceId, memberId, role)



### Example

```ts
import {
  Configuration,
  WorkspaceApi,
} from '';
import type { UpdateMemberRoleRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new WorkspaceApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    memberId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // WorkspaceRoles
    role: ...,
  } satisfies UpdateMemberRoleRequest;

  try {
    const data = await api.updateMemberRole(body);
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
| **memberId** | `string` |  | [Defaults to `undefined`] |
| **role** | `WorkspaceRoles` |  | [Defaults to `undefined`] [Enum: OWNER, ADMIN, MEMBER] |

### Return type

[**WorkspaceMember**](WorkspaceMember.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

