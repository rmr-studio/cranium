
# NotificationInboxItem


## Properties

Name | Type
------------ | -------------
`id` | string
`type` | [NotificationType](NotificationType.md)
`content` | [CreateNotificationRequestContent](CreateNotificationRequestContent.md)
`referenceType` | [NotificationReferenceType](NotificationReferenceType.md)
`referenceId` | string
`resolved` | boolean
`read` | boolean
`createdAt` | Date

## Example

```typescript
import type { NotificationInboxItem } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "type": null,
  "content": null,
  "referenceType": null,
  "referenceId": null,
  "resolved": null,
  "read": null,
  "createdAt": null,
} satisfies NotificationInboxItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NotificationInboxItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


