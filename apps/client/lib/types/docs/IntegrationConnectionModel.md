
# IntegrationConnectionModel


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`integrationId` | string
`nangoConnectionId` | string
`status` | [ConnectionStatus](ConnectionStatus.md)
`connectionMetadata` | { [key: string]: object; }
`createdAt` | Date
`updatedAt` | Date

## Example

```typescript
import type { IntegrationConnectionModel } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "integrationId": null,
  "nangoConnectionId": null,
  "status": null,
  "connectionMetadata": null,
  "createdAt": null,
  "updatedAt": null,
} satisfies IntegrationConnectionModel

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IntegrationConnectionModel
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


