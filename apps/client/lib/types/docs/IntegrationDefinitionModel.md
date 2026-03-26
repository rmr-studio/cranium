
# IntegrationDefinitionModel


## Properties

Name | Type
------------ | -------------
`id` | string
`slug` | string
`name` | string
`iconUrl` | string
`description` | string
`category` | [IntegrationCategory](IntegrationCategory.md)
`nangoProviderKey` | string
`capabilities` | { [key: string]: object; }
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { IntegrationDefinitionModel } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "slug": null,
  "name": null,
  "iconUrl": null,
  "description": null,
  "category": null,
  "nangoProviderKey": null,
  "capabilities": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies IntegrationDefinitionModel

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IntegrationDefinitionModel
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


