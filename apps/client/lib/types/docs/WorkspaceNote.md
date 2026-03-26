
# WorkspaceNote


## Properties

Name | Type
------------ | -------------
`id` | string
`entityId` | string
`workspaceId` | string
`title` | string
`content` | Array&lt;{ [key: string]: object; }&gt;
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string
`entityDisplayName` | string
`entityTypeKey` | string
`entityTypeIcon` | string
`entityTypeColour` | string

## Example

```typescript
import type { WorkspaceNote } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "entityId": null,
  "workspaceId": null,
  "title": null,
  "content": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
  "entityDisplayName": null,
  "entityTypeKey": null,
  "entityTypeIcon": null,
  "entityTypeColour": null,
} satisfies WorkspaceNote

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkspaceNote
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


