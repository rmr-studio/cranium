
# WorkflowNodeMetadata


## Properties

Name | Type
------------ | -------------
`type` | [WorkflowNodeType](WorkflowNodeType.md)
`subType` | string
`metadata` | [WorkflowNodeTypeMetadata](WorkflowNodeTypeMetadata.md)
`schema` | [Array&lt;WorkflowNodeConfigField&gt;](WorkflowNodeConfigField.md)
`outputMetadata` | [WorkflowNodeOutputMetadata](WorkflowNodeOutputMetadata.md)

## Example

```typescript
import type { WorkflowNodeMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "type": null,
  "subType": null,
  "metadata": null,
  "schema": null,
  "outputMetadata": null,
} satisfies WorkflowNodeMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowNodeMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


