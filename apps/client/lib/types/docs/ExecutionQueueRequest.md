
# ExecutionQueueRequest


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`jobType` | [ExecutionJobType](ExecutionJobType.md)
`entityId` | string
`workflowDefinitionId` | string
`executionId` | string
`status` | [ExecutionQueueStatus](ExecutionQueueStatus.md)
`createdAt` | Date
`claimedAt` | Date
`dispatchedAt` | Date
`attemptCount` | number
`lastError` | string

## Example

```typescript
import type { ExecutionQueueRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "jobType": null,
  "entityId": null,
  "workflowDefinitionId": null,
  "executionId": null,
  "status": null,
  "createdAt": null,
  "claimedAt": null,
  "dispatchedAt": null,
  "attemptCount": null,
  "lastError": null,
} satisfies ExecutionQueueRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ExecutionQueueRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


