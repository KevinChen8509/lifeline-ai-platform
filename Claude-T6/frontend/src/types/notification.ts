export interface Notification {
  id: number
  type: string
  title: string
  content: string | null
  relatedId: number | null
  relatedType: string | null
  isRead: boolean
  createdAt: string
}

export interface NotificationPreference {
  endpointFailureEnabled: boolean
  endpointRecoveredEnabled: boolean
  pushDeadEnabled: boolean
  failureFrequency: string
}
