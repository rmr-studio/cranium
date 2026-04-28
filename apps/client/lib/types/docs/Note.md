
# Note


## Properties

Name | Type
------------ | -------------
`id` | string
`entityIds` | Array&lt;string&gt;
`workspaceId` | string
`title` | string
`content` | Array&lt;{ [key: string]: object; }&gt;
`sourceType` | [NoteSourceType](NoteSourceType.md)
`readonly` | boolean
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string

## Example

```typescript
import type { Note } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "entityIds": null,
  "workspaceId": null,
  "title": null,
  "content": null,
  "sourceType": null,
  "readonly": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
} satisfies Note

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Note
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


