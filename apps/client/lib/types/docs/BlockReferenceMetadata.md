
# BlockReferenceMetadata


## Properties

Name | Type
------------ | -------------
`deletable` | boolean
`meta` | [BlockMeta](BlockMeta.md)
`readonly` | boolean
`type` | [BlockMetadataType](BlockMetadataType.md)
`fetchPolicy` | [BlockReferenceFetchPolicy](BlockReferenceFetchPolicy.md)
`path` | string
`expandDepth` | number
`item` | [ReferenceItem](ReferenceItem.md)

## Example

```typescript
import type { BlockReferenceMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "deletable": null,
  "meta": null,
  "readonly": null,
  "type": null,
  "fetchPolicy": null,
  "path": null,
  "expandDepth": null,
  "item": null,
} satisfies BlockReferenceMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BlockReferenceMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


