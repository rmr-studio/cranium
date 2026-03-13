
# WorkflowBulkUpdateEntityActionConfig

Configuration for BULK_UPDATE_ENTITY action nodes that apply identical field updates to all entities matching a query.

## Properties

Name | Type
------------ | -------------
`version` | number
`query` | [EntityQuery](EntityQuery.md)
`payload` | { [key: string]: string; }
`errorHandling` | [BulkUpdateErrorHandling](BulkUpdateErrorHandling.md)
`pagination` | [QueryPagination](QueryPagination.md)
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`subType` | [WorkflowActionType](WorkflowActionType.md)
`config` | { [key: string]: object; }
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowBulkUpdateEntityActionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "query": null,
  "payload": null,
  "errorHandling": null,
  "pagination": null,
  "timeoutSeconds": null,
  "configSchema": null,
  "subType": null,
  "config": null,
  "type": null,
} satisfies WorkflowBulkUpdateEntityActionConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowBulkUpdateEntityActionConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


