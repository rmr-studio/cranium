
# CreateBusinessDefinitionRequest


## Properties

Name | Type
------------ | -------------
`term` | string
`definition` | string
`category` | [DefinitionCategory](DefinitionCategory.md)
`source` | [DefinitionSource](DefinitionSource.md)
`entityTypeRefs` | Array&lt;string&gt;
`attributeRefs` | Array&lt;string&gt;
`isCustomized` | boolean

## Example

```typescript
import type { CreateBusinessDefinitionRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "term": null,
  "definition": null,
  "category": null,
  "source": null,
  "entityTypeRefs": null,
  "attributeRefs": null,
  "isCustomized": null,
} satisfies CreateBusinessDefinitionRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateBusinessDefinitionRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


