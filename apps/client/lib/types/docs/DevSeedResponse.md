
# DevSeedResponse


## Properties

Name | Type
------------ | -------------
`alreadySeeded` | boolean
`templateKey` | string
`entitiesCreated` | number
`relationshipsCreated` | number
`details` | [{ [key: string]: EntityTypeSeedDetail; }](EntityTypeSeedDetail.md)

## Example

```typescript
import type { DevSeedResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "alreadySeeded": null,
  "templateKey": null,
  "entitiesCreated": null,
  "relationshipsCreated": null,
  "details": null,
} satisfies DevSeedResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DevSeedResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


