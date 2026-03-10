
# ReferenceMetadata


## Properties

Name | Type
------------ | -------------
`deletable` | boolean
`meta` | [BlockMeta](BlockMeta.md)
`readonly` | boolean
`type` | [BlockMetadataType](BlockMetadataType.md)
`fetchPolicy` | [BlockReferenceFetchPolicy](BlockReferenceFetchPolicy.md)
`path` | string

## Example

```typescript
import type { ReferenceMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "deletable": null,
  "meta": null,
  "readonly": null,
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


