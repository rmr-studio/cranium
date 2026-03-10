
# BundleInstallationResponse


## Properties

Name | Type
------------ | -------------
`bundleKey` | string
`bundleName` | string
`templatesInstalled` | Array&lt;string&gt;
`templatesSkipped` | Array&lt;string&gt;
`entityTypesCreated` | number
`relationshipsCreated` | number
`entityTypes` | [Array&lt;CreatedEntityTypeSummary&gt;](CreatedEntityTypeSummary.md)

## Example

```typescript
import type { BundleInstallationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "bundleKey": null,
  "bundleName": null,
  "templatesInstalled": null,
  "templatesSkipped": null,
  "entityTypesCreated": null,
  "relationshipsCreated": null,
  "entityTypes": null,
} satisfies BundleInstallationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BundleInstallationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


