
# WorkflowUpdateEntityActionConfig

Configuration for UPDATE_ENTITY action nodes.

## Properties

Name | Type
------------ | -------------
`version` | number
`entityId` | string
`payload` | { [key: string]: string; }
`timeoutSeconds` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`subType` | [WorkflowActionType](WorkflowActionType.md)
`config` | { [key: string]: object; }
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowUpdateEntityActionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "entityId": null,
  "payload": null,
  "timeoutSeconds": null,
  "configSchema": null,
  "subType": null,
  "config": null,
  "type": null,
} satisfies WorkflowUpdateEntityActionConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowUpdateEntityActionConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


