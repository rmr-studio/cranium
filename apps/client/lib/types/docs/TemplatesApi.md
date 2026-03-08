# TemplatesApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**installBundle**](TemplatesApi.md#installbundleoperation) | **POST** /api/v1/templates/{workspaceId}/install-bundle | Install bundle into workspace |
| [**installTemplate**](TemplatesApi.md#installtemplateoperation) | **POST** /api/v1/templates/{workspaceId}/install | Install template into workspace |
| [**listBundles**](TemplatesApi.md#listbundles) | **GET** /api/v1/templates/bundles | List available bundles |
| [**listTemplates**](TemplatesApi.md#listtemplates) | **GET** /api/v1/templates | List available templates |



## installBundle

> BundleInstallationResponse installBundle(workspaceId, installBundleRequest)

Install bundle into workspace

Installs all templates in a bundle into the specified workspace. Templates already installed are skipped. Installation is atomic.

### Example

```ts
import {
  Configuration,
  TemplatesApi,
} from '';
import type { InstallBundleOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TemplatesApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // InstallBundleRequest
    installBundleRequest: ...,
  } satisfies InstallBundleOperationRequest;

  try {
    const data = await api.installBundle(body);
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
| **installBundleRequest** | [InstallBundleRequest](InstallBundleRequest.md) |  | |

### Return type

[**BundleInstallationResponse**](BundleInstallationResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Bundle installed successfully |  -  |
| **403** | No access to workspace |  -  |
| **404** | Bundle not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## installTemplate

> TemplateInstallationResponse installTemplate(workspaceId, installTemplateRequest)

Install template into workspace

Installs a template into the specified workspace, creating all entity types, relationships, and semantic metadata defined in the template manifest. Installation is atomic -- if any step fails, nothing is created.

### Example

```ts
import {
  Configuration,
  TemplatesApi,
} from '';
import type { InstallTemplateOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TemplatesApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // InstallTemplateRequest
    installTemplateRequest: ...,
  } satisfies InstallTemplateOperationRequest;

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
| **installTemplateRequest** | [InstallTemplateRequest](InstallTemplateRequest.md) |  | |

### Return type

[**TemplateInstallationResponse**](TemplateInstallationResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Template installed successfully |  -  |
| **403** | No access to workspace |  -  |
| **404** | Template not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listBundles

> Array&lt;BundleDetail&gt; listBundles()

List available bundles

Returns all bundles available for installation. Each bundle is a curated collection of templates.

### Example

```ts
import {
  Configuration,
  TemplatesApi,
} from '';
import type { ListBundlesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TemplatesApi(config);

  try {
    const data = await api.listBundles();
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

[**Array&lt;BundleDetail&gt;**](BundleDetail.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Bundles retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listTemplates

> Array&lt;ManifestSummary&gt; listTemplates()

List available templates

Returns all templates available for installation. No workspace scoping -- catalog is global.

### Example

```ts
import {
  Configuration,
  TemplatesApi,
} from '';
import type { ListTemplatesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new TemplatesApi(config);

  try {
    const data = await api.listTemplates();
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

[**Array&lt;ManifestSummary&gt;**](ManifestSummary.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Templates retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

