
# CreateNotificationRequest


## Properties

Name | Type
------------ | -------------
`workspaceId` | string
`userId` | string
`type` | [NotificationType](NotificationType.md)
`content` | [CreateNotificationRequestContent](CreateNotificationRequestContent.md)
`referenceType` | [NotificationReferenceType](NotificationReferenceType.md)
`referenceId` | string
`expiresAt` | Date

## Example

```typescript
import type { CreateNotificationRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "workspaceId": null,
  "userId": null,
  "type": null,
  "content": null,
  "referenceType": null,
  "referenceId": null,
  "expiresAt": null,
} satisfies CreateNotificationRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateNotificationRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


