
# EntityLink


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`definitionId` | string
`sourceEntityId` | string
`icon` | [Icon](Icon.md)
`key` | string
`label` | string

## Example

```typescript
import type { EntityLink } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "definitionId": null,
  "sourceEntityId": null,
  "icon": null,
  "key": null,
  "label": null,
} satisfies EntityLink

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityLink
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


