
# EntityReferenceMetadata


## Properties

Name | Type
------------ | -------------
`deletable` | boolean
`meta` | [BlockMeta](BlockMeta.md)
`readonly` | boolean
`type` | [BlockMetadataType](BlockMetadataType.md)
`fetchPolicy` | [BlockReferenceFetchPolicy](BlockReferenceFetchPolicy.md)
`path` | string
`presentation` | [Presentation](Presentation.md)
`items` | [Array&lt;ReferenceItem&gt;](ReferenceItem.md)
`projection` | [Projection](Projection.md)
`listType` | [Entity](Entity.md)
`display` | [ListDisplayConfig](ListDisplayConfig.md)
`config` | [ListConfig](ListConfig.md)
`allowDuplicates` | boolean

## Example

```typescript
import type { EntityReferenceMetadata } from ''

// TODO: Update the object below with actual values
const example = {
  "deletable": null,
  "meta": null,
  "readonly": null,
  "type": null,
  "fetchPolicy": null,
  "path": null,
  "presentation": null,
  "items": null,
  "projection": null,
  "listType": null,
  "display": null,
  "config": null,
  "allowDuplicates": null,
} satisfies EntityReferenceMetadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EntityReferenceMetadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


