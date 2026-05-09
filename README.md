# Notification-Scala-Pet

Small notification app with a Scala (Play Framework) backend and a Vite/React frontend.

- Frontend: `client/notification`
- Backend: `server/notification-server`


# Planned Architecture

```
┌──────────────────────┐
│      Frontend        │
│  User Performed      │
│      Actions         │
└──────────┬───────────┘
           │
           ▼

┌──────────────────────┐
│        Kafka         │
│  Event Collection    │
│  + Durable Storage   │
└──────────┬───────────┘
           │
           │
           ▼ 
┌──────────────────────┐
│        storing       │
│   the metrics in the │
│   reddis for traffic │
│      Monitoring      │      
│  Event Collection    │
│  + Durable Storage   │
└──────────┬───────────┘
 ┌─────────┴──────────────────────────┐
 │                                    │
 ▼                                    ▼

┌──────────────────────┐    ┌──────────────────────┐
│ Notification         │    │ DB Consumer          │
│ Consumer             │    │                      │
│                      │    │ Consume Kafka Logs   │
│ Consume Events       │    │ and Store Slowly     │
│ Process Notifications│    │ into Database        │
└──────────┬───────────┘    └──────────┬───────────┘
           │                           │
           ▼                           ▼

┌──────────────────────┐    ┌──────────────────────┐
│      RabbitMQ        │    │      Database        │
│ Real-time Push Queue │    │ Event/Notification   │
└──────────┬───────────┘    │ Storage              │
           │                └──────────────────────┘
           ▼

┌──────────────────────┐
│ WebSocket / Push     │
│ Notification Gateway │
└──────────┬───────────┘
           │
           ▼

┌──────────────────────┐
│        Users         │
│ Receive Notifications│
└──────────┬───────────┘
           │
           ▼

┌──────────────────────┐
│ Store Popped /       │
│ Delivered Status     │
│ into Database        │
└──────────────────────┘
```