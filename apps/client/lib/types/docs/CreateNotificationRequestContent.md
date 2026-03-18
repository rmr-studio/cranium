
# CreateNotificationRequestContent


## Properties

Name | Type
------------ | -------------
`title` | string
`message` | string
`type` | string
`sourceLabel` | string
`contextSummary` | string
`priority` | [ReviewPriority](ReviewPriority.md)
`severity` | [SystemSeverity](SystemSeverity.md)

## Example

```typescript
import type { CreateNotificationRequestContent } from ''

// TODO: Update the object below with actual values
const example = {
  "title": null,
  "message": null,
  "type": null,
  "sourceLabel": null,
  "contextSummary": null,
  "priority": null,
  "severity": null,
} satisfies CreateNotificationRequestContent

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateNotificationRequestContent
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


