# BlockApi

All URIs are relative to *http://localhost:8081*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getBlockEnvironment**](BlockApi.md#getblockenvironment) | **GET** /api/v1/block/environment/workspace/{workspaceId}/type/{type}/id/{entityId} | Get Block Environment |
| [**getBlockTypeByKey**](BlockApi.md#getblocktypebykey) | **GET** /api/v1/block/schema/key/{key} | Get block type by key |
| [**getBlockTypes**](BlockApi.md#getblocktypes) | **GET** /api/v1/block/schema/workspace/{workspaceId} | Get block types for workspace |
| [**hydrateBlocks**](BlockApi.md#hydrateblocksoperation) | **POST** /api/v1/block/environment/hydrate | Hydrate Blocks |
| [**overwriteBlockEnvironment**](BlockApi.md#overwriteblockenvironment) | **POST** /api/v1/block/environment/overwrite | Overwrite Block Environment |
| [**publishBlockType**](BlockApi.md#publishblocktype) | **POST** /api/v1/block/schema/ | Create a new block type |
| [**saveBlockEnvironment**](BlockApi.md#saveblockenvironment) | **POST** /api/v1/block/environment/ | Save Block Environment |
| [**updateBlockType**](BlockApi.md#updateblocktype) | **PUT** /api/v1/block/schema/{blockTypeId} | Update an existing block type |



## getBlockEnvironment

> BlockEnvironment getBlockEnvironment(workspaceId, type, entityId)

Get Block Environment

Retrieves the block environment for the specified entity. Creates a default layout if none exists (lazy initialization).

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { GetBlockEnvironmentRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // ApplicationEntityType
    type: ...,
    // string
    entityId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetBlockEnvironmentRequest;

  try {
    const data = await api.getBlockEnvironment(body);
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
| **type** | `ApplicationEntityType` |  | [Defaults to `undefined`] [Enum: WORKSPACE, BLOCK_TYPE, BLOCK, USER, ENTITY, ENTITY_TYPE, WORKFLOW_DEFINITION, WORKFLOW_NODE, WORKFLOW_EDGE, INTEGRATION_CONNECTION, FILE, NOTIFICATION, INTEGRATION_INSTALLATION, NOTE] |
| **entityId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**BlockEnvironment**](BlockEnvironment.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Environment retrieved successfully |  -  |
| **404** | Entity not found |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Forbidden - insufficient permissions |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getBlockTypeByKey

> BlockType getBlockTypeByKey(key)

Get block type by key

Retrieves a block type by its unique key.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { GetBlockTypeByKeyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // string
    key: key_example,
  } satisfies GetBlockTypeByKeyRequest;

  try {
    const data = await api.getBlockTypeByKey(body);
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
| **key** | `string` |  | [Defaults to `undefined`] |

### Return type

[**BlockType**](BlockType.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Block type retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Block type not found |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getBlockTypes

> Array&lt;BlockType&gt; getBlockTypes(workspaceId)

Get block types for workspace

Retrieves all block types associated with a specific workspace.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { GetBlockTypesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // string
    workspaceId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
  } satisfies GetBlockTypesRequest;

  try {
    const data = await api.getBlockTypes(body);
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

[**Array&lt;BlockType&gt;**](BlockType.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Block types retrieved successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | No block types found for the workspace |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## hydrateBlocks

> { [key: string]: BlockHydrationResult; } hydrateBlocks(hydrateBlocksRequest)

Hydrate Blocks

Resolves entity references for one or more blocks in a single batched request. This is used for progressive loading of entity data without fetching everything upfront. Only blocks with entity reference metadata will be hydrated; other blocks are skipped.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { HydrateBlocksOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // HydrateBlocksRequest
    hydrateBlocksRequest: ...,
  } satisfies HydrateBlocksOperationRequest;

  try {
    const data = await api.hydrateBlocks(body);
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
| **hydrateBlocksRequest** | [HydrateBlocksRequest](HydrateBlocksRequest.md) |  | |

### Return type

[**{ [key: string]: BlockHydrationResult; }**](BlockHydrationResult.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Blocks hydrated successfully |  -  |
| **400** | Invalid request data |  -  |
| **401** | Unauthorized access |  -  |
| **403** | Forbidden - insufficient permissions |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## overwriteBlockEnvironment

> OverwriteEnvironmentResponse overwriteBlockEnvironment(overwriteEnvironmentRequest)

Overwrite Block Environment

Overwrites the entire block environment with the provided data.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { OverwriteBlockEnvironmentRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // OverwriteEnvironmentRequest
    overwriteEnvironmentRequest: ...,
  } satisfies OverwriteBlockEnvironmentRequest;

  try {
    const data = await api.overwriteBlockEnvironment(body);
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
| **overwriteEnvironmentRequest** | [OverwriteEnvironmentRequest](OverwriteEnvironmentRequest.md) |  | |

### Return type

[**OverwriteEnvironmentResponse**](OverwriteEnvironmentResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Environment overwritten successfully |  -  |
| **401** | Unauthorized access |  -  |
| **400** | Invalid request data |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## publishBlockType

> BlockType publishBlockType(createBlockTypeRequest)

Create a new block type

Creates and publishes a new block type based on the provided request data.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { PublishBlockTypeRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // CreateBlockTypeRequest
    createBlockTypeRequest: ...,
  } satisfies PublishBlockTypeRequest;

  try {
    const data = await api.publishBlockType(body);
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
| **createBlockTypeRequest** | [CreateBlockTypeRequest](CreateBlockTypeRequest.md) |  | |

### Return type

[**BlockType**](BlockType.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **201** | Block type created successfully |  -  |
| **401** | Unauthorized access |  -  |
| **400** | Invalid request data |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## saveBlockEnvironment

> SaveEnvironmentResponse saveBlockEnvironment(saveEnvironmentRequest)

Save Block Environment

Saves the block environment including layout and structural operations.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { SaveBlockEnvironmentRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // SaveEnvironmentRequest
    saveEnvironmentRequest: ...,
  } satisfies SaveBlockEnvironmentRequest;

  try {
    const data = await api.saveBlockEnvironment(body);
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
| **saveEnvironmentRequest** | [SaveEnvironmentRequest](SaveEnvironmentRequest.md) |  | |

### Return type

[**SaveEnvironmentResponse**](SaveEnvironmentResponse.md)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Environment saved successfully |  -  |
| **409** | Conflict in versioning when saving environment |  -  |
| **401** | Unauthorized access |  -  |
| **400** | Invalid request data |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateBlockType

> updateBlockType(blockTypeId, blockType)

Update an existing block type

Updates a block type with the specified ID. Does not allow changing the scope of the block type.

### Example

```ts
import {
  Configuration,
  BlockApi,
} from '';
import type { UpdateBlockTypeRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // Configure HTTP bearer authorization: bearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new BlockApi(config);

  const body = {
    // string
    blockTypeId: 38400000-8cf0-11bd-b23e-10b96e4ef00d,
    // BlockType
    blockType: ...,
  } satisfies UpdateBlockTypeRequest;

  try {
    const data = await api.updateBlockType(body);
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
| **blockTypeId** | `string` |  | [Defaults to `undefined`] |
| **blockType** | [BlockType](BlockType.md) |  | |

### Return type

`void` (Empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Block type updated successfully |  -  |
| **401** | Unauthorized access |  -  |
| **404** | Block type not found |  -  |
| **400** | Invalid request data |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

