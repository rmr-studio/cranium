
# SyncConfiguration


## Properties

Name | Type
------------ | -------------
`syncScope` | [SyncScope](SyncScope.md)
`syncWindowMonths` | number

## Example

```typescript
import type { SyncConfiguration } from ''

// TODO: Update the object below with actual values
const example = {
  "syncScope": null,
  "syncWindowMonths": null,
} satisfies SyncConfiguration

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SyncConfiguration
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


