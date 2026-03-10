
# TemplateInstallationResponse


## Properties

Name | Type
------------ | -------------
`templateKey` | string
`templateName` | string
`entityTypesCreated` | number
`relationshipsCreated` | number
`entityTypes` | [Array&lt;CreatedEntityTypeSummary&gt;](CreatedEntityTypeSummary.md)

## Example

```typescript
import type { TemplateInstallationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "templateKey": null,
  "templateName": null,
  "entityTypesCreated": null,
  "relationshipsCreated": null,
  "entityTypes": null,
} satisfies TemplateInstallationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TemplateInstallationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


