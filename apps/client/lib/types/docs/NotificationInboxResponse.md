
# NotificationInboxResponse


## Properties

Name | Type
------------ | -------------
`notifications` | [Array&lt;NotificationInboxItem&gt;](NotificationInboxItem.md)
`nextCursor` | string
`unreadCount` | number

## Example

```typescript
import type { NotificationInboxResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "notifications": null,
  "nextCursor": null,
  "unreadCount": null,
} satisfies NotificationInboxResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NotificationInboxResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


