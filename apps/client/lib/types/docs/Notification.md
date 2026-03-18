
# Notification


## Properties

Name | Type
------------ | -------------
`id` | string
`workspaceId` | string
`userId` | string
`type` | [NotificationType](NotificationType.md)
`content` | [CreateNotificationRequestContent](CreateNotificationRequestContent.md)
`referenceType` | [NotificationReferenceType](NotificationReferenceType.md)
`referenceId` | string
`resolved` | boolean
`resolvedAt` | Date
`expiresAt` | Date
`createdAt` | Date
`updatedAt` | Date
`createdBy` | string
`updatedBy` | string

## Example

```typescript
import type { Notification } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "workspaceId": null,
  "userId": null,
  "type": null,
  "content": null,
  "referenceType": null,
  "referenceId": null,
  "resolved": null,
  "resolvedAt": null,
  "expiresAt": null,
  "createdAt": null,
  "updatedAt": null,
  "createdBy": null,
  "updatedBy": null,
} satisfies Notification

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Notification
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


