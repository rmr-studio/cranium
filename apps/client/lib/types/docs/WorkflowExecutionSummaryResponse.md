
# WorkflowExecutionSummaryResponse


## Properties

Name | Type
------------ | -------------
`execution` | [WorkflowExecutionRecord](WorkflowExecutionRecord.md)
`nodes` | [Array&lt;WorkflowExecutionNodeRecord&gt;](WorkflowExecutionNodeRecord.md)
`failedNode` | [WorkflowExecutionNodeRecord](WorkflowExecutionNodeRecord.md)
`hasErrors` | boolean
`failedNodes` | [Array&lt;WorkflowExecutionNodeRecord&gt;](WorkflowExecutionNodeRecord.md)

## Example

```typescript
import type { WorkflowExecutionSummaryResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "execution": null,
  "nodes": null,
  "failedNode": null,
  "hasErrors": null,
  "failedNodes": null,
} satisfies WorkflowExecutionSummaryResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowExecutionSummaryResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


