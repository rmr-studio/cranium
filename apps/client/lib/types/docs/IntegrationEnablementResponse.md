
# IntegrationEnablementResponse


## Properties

Name | Type
------------ | -------------
`integrationDefinitionId` | string
`integrationName` | string
`integrationSlug` | string
`entityTypesCreated` | number
`entityTypesRestored` | number
`relationshipsCreated` | number
`entityTypes` | [Array&lt;EnabledEntityTypeSummary&gt;](EnabledEntityTypeSummary.md)
`syncConfig` | [SyncConfiguration](SyncConfiguration.md)

## Example

```typescript
import type { IntegrationEnablementResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "integrationDefinitionId": null,
  "integrationName": null,
  "integrationSlug": null,
  "entityTypesCreated": null,
  "entityTypesRestored": null,
  "relationshipsCreated": null,
  "entityTypes": null,
  "syncConfig": null,
} satisfies IntegrationEnablementResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IntegrationEnablementResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


