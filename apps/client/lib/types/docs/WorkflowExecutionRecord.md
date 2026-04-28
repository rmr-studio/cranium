
# WorkflowExecutionRecord


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`workflowDefinitionId` | string
`workflowVersionId` | string
`status` | [WorkflowStatus](WorkflowStatus.md)
`startedAt` | Date
`completedAt` | Date
`duration` | string
`triggerType` | [WorkflowTriggerType](WorkflowTriggerType.md)
`input` | object
`error` | object
`output` | object

## Example

```typescript
import type { WorkflowExecutionRecord } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "workflowDefinitionId": null,
  "workflowVersionId": null,
  "status": null,
  "startedAt": null,
  "completedAt": null,
  "duration": null,
  "triggerType": null,
  "input": null,
  "error": null,
  "output": null,
} satisfies WorkflowExecutionRecord

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkflowExecutionRecord
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


