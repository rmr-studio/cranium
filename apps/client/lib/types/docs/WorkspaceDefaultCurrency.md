
# WorkspaceDefaultCurrency


## Properties

Name | Type
------------ | -------------
`currencyCode` | string
`numericCode` | number
`numericCodeAsString` | string
`displayName` | string
`symbol` | string
`defaultFractionDigits` | number

## Example

```typescript
import type { WorkspaceDefaultCurrency } from ''

// TODO: Update the object below with actual values
const example = {
  "currencyCode": null,
  "numericCode": null,
  "numericCodeAsString": null,
  "displayName": null,
  "symbol": null,
  "defaultFractionDigits": null,
} satisfies WorkspaceDefaultCurrency

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as WorkspaceDefaultCurrency
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


