
# UpdateNoteRequest


## Properties

Name | Type
------------ | -------------
`title` | string
`content` | Array&lt;{ [key: string]: object; }&gt;

## Example

```typescript
import type { UpdateNoteRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "title": null,
  "content": null,
} satisfies UpdateNoteRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateNoteRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


