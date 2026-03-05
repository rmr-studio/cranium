
# Metadata


## Properties

Name | Type
------------ | -------------
`readonly` | boolean
`deletable` | boolean
`meta` | [BlockMeta](BlockMeta.md)
`type` | [BlockMetadataType](BlockMetadataType.md)

## Example

```typescript
import type { Metadata } from ''

// TODO: Update the object below with actual values
const example = {
  "readonly": null,
  "deletable": null,
  "meta": null,
  "type": null,
} satisfies Metadata

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Metadata
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


