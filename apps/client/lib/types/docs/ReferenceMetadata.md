
# ReferenceMetadata


## Properties

Name | Type
------------ | -------------
`readonly` | boolean
`deletable` | boolean
`meta` | [BlockMeta](BlockMeta.md)
`type` | [BlockMetadataType](BlockMetadataType.md)
`fetchPolicy` | [BlockReferenceFetchPolicy](BlockReferenceFetchPolicy.md)
`path` | string

## Example

```typescript
import type { ReferenceMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "readonly": null,
  "deletable": null,
  "meta": null,
  "type": null,
  "fetchPolicy": null,
  "path": null,
} satisfies ReferenceMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ReferenceMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


