
# WorkflowFunctionConfig

Configuration for FUNCTION category nodes.

## Properties

Name | Type
------------ | -------------
`version` | number
`configSchema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`config` | { [key: string]: object; }
`type` | [WorkflowNodeType](WorkflowNodeType.md)

## Example

```typescript
import type { WorkflowFunctionConfig } from ''

// TODO: Update the object below with actual values
const example = {
  "version": null,
  "configSchema": null,
  "config": null,
  "type": null,
} satisfies WorkflowFunctionConfig

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowFunctionConfig
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


