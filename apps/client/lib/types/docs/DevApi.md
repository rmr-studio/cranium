# DevApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**reinstallTemplate**](DevApi.md#reinstalltemplate) | **POST** /api/v1/dev/seed/workspace/{workspaceId}/template/{templateKey}/reinstall | Re-install a template into a workspace |
| [**seedWorkspace**](DevApi.md#seedworkspace) | **POST** /api/v1/dev/seed/workspace/{workspaceId} | Seed workspace with mock entity data |



## reinstallTemplate

> TemplateInstallationResponse reinstallTemplate(workspaceId, templateKey)

Re-install a template into a workspace

Dev-only. Removes the existing template installation record (if present) and re-runs installation for the provided template key. Existing workspace entity types with matching keys are reused; missing types are created along with their relationships and semantic metadata.

### Example

```ts
import {
  Configuration,
  DevApi,
} from '';
import type { ReinstallTemplateRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DevApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    templateKey: templateKey_example,
  } satisfies ReinstallTemplateRequest;

  try {
    const data = await api.reinstallTemplate(body);
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
| **templateKey** | `string` |  | [Defaults to `undefined`] |

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
| **200** | Template re-installed successfully |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## seedWorkspace

> DevSeedResponse seedWorkspace(workspaceId)

Seed workspace with mock entity data

Populates the workspace with realistic mock entities and relationships based on its installed template. Idempotent — returns early if data already exists.

### Example

```ts
import {
  Configuration,
  DevApi,
} from '';
import type { SeedWorkspaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new DevApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies SeedWorkspaceRequest;

  try {
    const data = await api.seedWorkspace(body);
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

[**DevSeedResponse**](DevSeedResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Workspace seeded successfully or already seeded |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

