
# WorkflowExecutionNodeRecord


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`executionId` | string
`node` | [WorkflowNode](WorkflowNode.md)
`sequenceIndex` | number
`status` | [WorkflowStatus](WorkflowStatus.md)
`startedAt` | Date
`completedAt` | Date
`duration` | string
`attempt` | number
`input` | object
`output` | object
`error` | object

## Example

```typescript
import type { WorkflowExecutionNodeRecord } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "executionId": null,
  "node": null,
  "sequenceIndex": null,
  "status": null,
  "startedAt": null,
  "completedAt": null,
  "duration": null,
  "attempt": null,
  "input": null,
  "output": null,
  "error": null,
} satisfies WorkflowExecutionNodeRecord

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowExecutionNodeRecord
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


