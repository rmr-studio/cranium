
# SaveWorkspaceRequest


## Properties

Name | Type
------------ | -------------
`id` | string
`name` | string
`plan` | [WorkspacePlan](WorkspacePlan.md)
`defaultCurrency` | string
`isDefault` | boolean
`removeAvatar` | boolean

## Example

```typescript
import type { SaveWorkspaceRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "name": null,
  "plan": null,
  "defaultCurrency": null,
  "isDefault": null,
  "removeAvatar": null,
} satisfies SaveWorkspaceRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SaveWorkspaceRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


