# KnowledgeApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**bulkSetAttributeMetadata**](KnowledgeApi.md#bulksetattributemetadata) | **PUT** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/attributes/bulk | Set semantic metadata for multiple attributes (full replacement per attribute) |
| [**createDefinition**](KnowledgeApi.md#createdefinition) | **POST** /api/v1/knowledge/workspace/{workspaceId}/definitions | Create a new business definition |
| [**deleteDefinition**](KnowledgeApi.md#deletedefinition) | **DELETE** /api/v1/knowledge/workspace/{workspaceId}/definitions/{id} | Soft-delete a business definition |
| [**getAllMetadata**](KnowledgeApi.md#getallmetadata) | **GET** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/all | Get all semantic metadata for an entity type (entity type + attributes + relationships) |
| [**getAttributeMetadata**](KnowledgeApi.md#getattributemetadata) | **GET** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/attributes | Get semantic metadata for all attributes of an entity type |
| [**getDefinition**](KnowledgeApi.md#getdefinition) | **GET** /api/v1/knowledge/workspace/{workspaceId}/definitions/{id} | Get a single business definition by ID |
| [**getEntityTypeMetadata**](KnowledgeApi.md#getentitytypemetadata) | **GET** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId} | Get semantic metadata for an entity type |
| [**getRelationshipMetadata**](KnowledgeApi.md#getrelationshipmetadata) | **GET** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/relationships | Get semantic metadata for all relationships of an entity type |
| [**listDefinitions**](KnowledgeApi.md#listdefinitions) | **GET** /api/v1/knowledge/workspace/{workspaceId}/definitions | List all business definitions for a workspace |
| [**setAttributeMetadata**](KnowledgeApi.md#setattributemetadata) | **PUT** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/attribute/{attributeId} | Set semantic metadata for a single attribute (full replacement) |
| [**setEntityTypeMetadata**](KnowledgeApi.md#setentitytypemetadata) | **PUT** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId} | Set semantic metadata for an entity type (full replacement) |
| [**setRelationshipMetadata**](KnowledgeApi.md#setrelationshipmetadata) | **PUT** /api/v1/knowledge/workspace/{workspaceId}/entity-type/{entityTypeId}/relationship/{relationshipId} | Set semantic metadata for a single relationship (full replacement) |
| [**updateDefinition**](KnowledgeApi.md#updatedefinition) | **PUT** /api/v1/knowledge/workspace/{workspaceId}/definitions/{id} | Update a business definition |



## bulkSetAttributeMetadata

> Array&lt;EntityTypeSemanticMetadata&gt; bulkSetAttributeMetadata(workspaceId, entityTypeId, bulkSaveSemanticMetadataRequest)

Set semantic metadata for multiple attributes (full replacement per attribute)

Bulk upserts semantic metadata for multiple attributes of an entity type. All fields are fully replaced per attribute on every call.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { BulkSetAttributeMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // Array<BulkSaveSemanticMetadataRequest>
    bulkSaveSemanticMetadataRequest: ...,
  } satisfies BulkSetAttributeMetadataRequest;

  try {
    const data = await api.bulkSetAttributeMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |
| **bulkSaveSemanticMetadataRequest** | `Array<BulkSaveSemanticMetadataRequest>` |  | |

### Return type

[**Array&lt;EntityTypeSemanticMetadata&gt;**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Attribute metadata bulk saved successfully |  -  |
| **400** | Invalid classification value |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createDefinition

> WorkspaceBusinessDefinition createDefinition(workspaceId, createBusinessDefinitionRequest)

Create a new business definition

Creates a workspace-scoped business definition. Requires workspace ADMIN role.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { CreateDefinitionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // CreateBusinessDefinitionRequest
    createBusinessDefinitionRequest: ...,
  } satisfies CreateDefinitionRequest;

  try {
    const data = await api.createDefinition(body);
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
| **createBusinessDefinitionRequest** | [CreateBusinessDefinitionRequest](CreateBusinessDefinitionRequest.md) |  | |

### Return type

[**WorkspaceBusinessDefinition**](WorkspaceBusinessDefinition.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Definition created successfully |  -  |
| **400** | Invalid input |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Insufficient permissions |  -  |
| **409** | Duplicate term in workspace |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteDefinition

> deleteDefinition(workspaceId, id)

Soft-delete a business definition

Marks a business definition as deleted. Requires workspace ADMIN role.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { DeleteDefinitionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies DeleteDefinitionRequest;

  try {
    const data = await api.deleteDefinition(body);
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
| **204** | Definition deleted successfully |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Insufficient permissions |  -  |
| **404** | Definition not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getAllMetadata

> SemanticMetadataBundle getAllMetadata(workspaceId, entityTypeId)

Get all semantic metadata for an entity type (entity type + attributes + relationships)

Retrieves all semantic metadata records for the entity type itself, its attributes, and its relationships, grouped into a single bundle.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { GetAllMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetAllMetadataRequest;

  try {
    const data = await api.getAllMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**SemanticMetadataBundle**](SemanticMetadataBundle.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Metadata bundle retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getAttributeMetadata

> Array&lt;EntityTypeSemanticMetadata&gt; getAttributeMetadata(workspaceId, entityTypeId)

Get semantic metadata for all attributes of an entity type

Retrieves all attribute-level semantic metadata records for the given entity type.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { GetAttributeMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetAttributeMetadataRequest;

  try {
    const data = await api.getAttributeMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;EntityTypeSemanticMetadata&gt;**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Attribute metadata retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getDefinition

> WorkspaceBusinessDefinition getDefinition(workspaceId, id)

Get a single business definition by ID

Retrieves a specific business definition within the workspace.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { GetDefinitionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetDefinitionRequest;

  try {
    const data = await api.getDefinition(body);
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

[**WorkspaceBusinessDefinition**](WorkspaceBusinessDefinition.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Definition retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Definition not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getEntityTypeMetadata

> EntityTypeSemanticMetadata getEntityTypeMetadata(workspaceId, entityTypeId)

Get semantic metadata for an entity type

Retrieves the semantic metadata record for the entity type itself (not its attributes or relationships).

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { GetEntityTypeMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetEntityTypeMetadataRequest;

  try {
    const data = await api.getEntityTypeMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**EntityTypeSemanticMetadata**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Semantic metadata retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type or metadata not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getRelationshipMetadata

> Array&lt;EntityTypeSemanticMetadata&gt; getRelationshipMetadata(workspaceId, entityTypeId)

Get semantic metadata for all relationships of an entity type

Retrieves all relationship-level semantic metadata records for the given entity type.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { GetRelationshipMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetRelationshipMetadataRequest;

  try {
    const data = await api.getRelationshipMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;EntityTypeSemanticMetadata&gt;**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Relationship metadata retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listDefinitions

> Array&lt;WorkspaceBusinessDefinition&gt; listDefinitions(workspaceId, status, category)

List all business definitions for a workspace

Retrieves all active business definitions, optionally filtered by status and/or category.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { ListDefinitionsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // DefinitionStatus (optional)
    status: ...,
    // DefinitionCategory (optional)
    category: ...,
  } satisfies ListDefinitionsRequest;

  try {
    const data = await api.listDefinitions(body);
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
| **status** | `DefinitionStatus` |  | [Optional] [Defaults to `undefined`] [Enum: ACTIVE, SUGGESTED] |
| **category** | `DefinitionCategory` |  | [Optional] [Defaults to `undefined`] [Enum: METRIC, SEGMENT, STATUS, LIFECYCLE_STAGE, CUSTOM] |

### Return type

[**Array&lt;WorkspaceBusinessDefinition&gt;**](WorkspaceBusinessDefinition.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Definitions retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## setAttributeMetadata

> EntityTypeSemanticMetadata setAttributeMetadata(workspaceId, entityTypeId, attributeId, saveSemanticMetadataRequest)

Set semantic metadata for a single attribute (full replacement)

Upserts semantic metadata for a specific attribute. All fields are fully replaced on every call.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { SetAttributeMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    attributeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SaveSemanticMetadataRequest
    saveSemanticMetadataRequest: ...,
  } satisfies SetAttributeMetadataRequest;

  try {
    const data = await api.setAttributeMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |
| **attributeId** | `string` |  | [Defaults to `undefined`] |
| **saveSemanticMetadataRequest** | [SaveSemanticMetadataRequest](SaveSemanticMetadataRequest.md) |  | |

### Return type

[**EntityTypeSemanticMetadata**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Attribute metadata saved successfully |  -  |
| **400** | Invalid classification value |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## setEntityTypeMetadata

> EntityTypeSemanticMetadata setEntityTypeMetadata(workspaceId, entityTypeId, saveSemanticMetadataRequest)

Set semantic metadata for an entity type (full replacement)

Upserts the semantic metadata for the entity type itself. All fields are fully replaced on every call.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { SetEntityTypeMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SaveSemanticMetadataRequest
    saveSemanticMetadataRequest: ...,
  } satisfies SetEntityTypeMetadataRequest;

  try {
    const data = await api.setEntityTypeMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |
| **saveSemanticMetadataRequest** | [SaveSemanticMetadataRequest](SaveSemanticMetadataRequest.md) |  | |

### Return type

[**EntityTypeSemanticMetadata**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Semantic metadata saved successfully |  -  |
| **400** | Invalid classification value |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## setRelationshipMetadata

> EntityTypeSemanticMetadata setRelationshipMetadata(workspaceId, entityTypeId, relationshipId, saveSemanticMetadataRequest)

Set semantic metadata for a single relationship (full replacement)

Upserts semantic metadata for a specific relationship definition. All fields are fully replaced on every call.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { SetRelationshipMetadataRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    entityTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    relationshipId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // SaveSemanticMetadataRequest
    saveSemanticMetadataRequest: ...,
  } satisfies SetRelationshipMetadataRequest;

  try {
    const data = await api.setRelationshipMetadata(body);
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
| **entityTypeId** | `string` |  | [Defaults to `undefined`] |
| **relationshipId** | `string` |  | [Defaults to `undefined`] |
| **saveSemanticMetadataRequest** | [SaveSemanticMetadataRequest](SaveSemanticMetadataRequest.md) |  | |

### Return type

[**EntityTypeSemanticMetadata**](EntityTypeSemanticMetadata.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Relationship metadata saved successfully |  -  |
| **400** | Invalid classification value |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Entity type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateDefinition

> WorkspaceBusinessDefinition updateDefinition(workspaceId, id, updateBusinessDefinitionRequest)

Update a business definition

Full replacement update with optimistic locking. Requires workspace ADMIN role.

### Example

```ts
import {
  Configuration,
  KnowledgeApi,
} from '';
import type { UpdateDefinitionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new KnowledgeApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // string
    id: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // UpdateBusinessDefinitionRequest
    updateBusinessDefinitionRequest: ...,
  } satisfies UpdateDefinitionRequest;

  try {
    const data = await api.updateDefinition(body);
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
| **updateBusinessDefinitionRequest** | [UpdateBusinessDefinitionRequest](UpdateBusinessDefinitionRequest.md) |  | |

### Return type

[**WorkspaceBusinessDefinition**](WorkspaceBusinessDefinition.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Definition updated successfully |  -  |
| **400** | Invalid input |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Insufficient permissions |  -  |
| **404** | Definition not found |  -  |
| **409** | Version conflict or duplicate term |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

