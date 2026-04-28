
# SchemaOptions


## Properties

Name | Type
------------ | -------------
`defaultValue` | [SchemaOptionsDefaultValue](SchemaOptionsDefaultValue.md)
`prefix` | string
`regex` | string
`_enum` | Array&lt;string&gt;
`enumSorting` | [OptionSortingType](OptionSortingType.md)
`minLength` | number
`maxLength` | number
`minimum` | number
`maximum` | number
`minDate` | Date
`maxDate` | Date

## Example

```typescript
import type { SchemaOptions } from ''

// TODO: Update the object below with actual values
const example = {
  "defaultValue": null,
  "prefix": null,
  "regex": null,
  "_enum": null,
  "enumSorting": null,
  "minLength": null,
  "maxLength": null,
  "minimum": null,
  "maximum": null,
  "minDate": null,
  "maxDate": null,
} satisfies SchemaOptions

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SchemaOptions
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


