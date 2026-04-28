
# WorkflowDeleteEntityActionConfig

Configuration for DELETE_ENTITY action nodes.

## Properties

Name | Type
------------ | -------------
`version` | number
`entityId` | string
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`subType` | [WorkflowActionType](WorkflowActionType.md)
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowDeleteEntityActionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "entityId": 550e8400-e29b-41d4-a716-446655440000,
  "timeoutSeconds": null,
  "configSchema": null,
  "config": null,
  "subType": null,
  "type": null,
} satisfies WorkflowDeleteEntityActionConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowDeleteEntityActionConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


