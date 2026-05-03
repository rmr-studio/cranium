
# Block


## Properties

Name | Type
------------ | -------------
`id` | string
`name` | string
`workspaceId` | string
`type` | [BlockType](BlockType.md)
`payload` | [Metadata](Metadata.md)
`validationErrors` | Array&lt;string&gt;
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string

## Example

```typescript
import type { Block } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "name": null,
  "workspaceId": null,
  "type": null,
  "payload": null,
  "validationErrors": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
} satisfies Block

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Block
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


