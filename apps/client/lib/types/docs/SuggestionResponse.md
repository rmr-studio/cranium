
# SuggestionResponse


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`sourceEntityId` | string
`targetEntityId` | string
`status` | [MatchSuggestionStatus](MatchSuggestionStatus.md)
`confidenceScore` | number
`signals` | Array&lt;{ [key: string]: object; }&gt;
`resolvedBy` | string
`resolvedAt` | Date
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { SuggestionResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "sourceEntityId": null,
  "targetEntityId": null,
  "status": null,
  "confidenceScore": null,
  "signals": null,
  "resolvedBy": null,
  "resolvedAt": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies SuggestionResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SuggestionResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


