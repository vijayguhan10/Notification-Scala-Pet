# RabbitMQ Notification Delivery Architecture


                    ┌──────────────────────────────┐
                    │ Notification Builder Service │
                    │------------------------------│
                    │ Processed Notification       │
                    │ Email Payload Ready          │
                    └──────────────┬───────────────┘
                                   │
                                   │ Publish Message
                                   ▼

          ┌──────────────────────────────────────────────────┐
          │                    RabbitMQ                     │
          │──────────────────────────────────────────────────│
          │                                                  │
          │                  Direct Exchange                 │
          │                                                  │
          └───────────────┬──────────────────┬──────────────┘
                          │                  │
                          │ Routing          │ Routing
                          ▼                  ▼

         ┌──────────────────────┐   ┌──────────────────────┐
         │ Email Queue          │   │ DB Logging Queue     │
         │----------------------│   │----------------------│
         │ Notification Events  │   │ Delivery Events      │
         └──────────┬───────────┘   └──────────┬───────────┘
                    │                          │
                    ▼                          ▼

         ┌──────────────────────┐   ┌──────────────────────┐
         │ Email Consumer       │   │ DB Consumer          │
         │----------------------│   │----------------------│
         │ Send Email           │   │ Persist Logs         │
         │ SMTP/API Call        │   │ Store Audit Trail    │
         └──────────┬───────────┘   └──────────┬───────────┘
                    │                          │
                    ▼                          ▼

         ┌──────────────────────┐   ┌──────────────────────┐
         │ Email Provider       │   │ PostgreSQL           │
         │----------------------│   │----------------------│
         │ SendGrid / SES       │   │ Notification Logs    │
         │ Mailgun / SMTP       │   │ Delivery Status      │
         └──────────────────────┘   └──────────────────────┘
```

---

# DLQ (Dead Letter Queue) Architecture

                    ┌───────────────────────────┐
                    │       Main Exchange       │
                    └─────────────┬─────────────┘
                                  │
                 ┌────────────────┴────────────────┐
                 │                                 │
                 ▼                                 ▼

       ┌──────────────────┐              ┌──────────────────┐
       │ Email Queue      │              │ DB Queue         │
       └────────┬─────────┘              └────────┬─────────┘
                │                                 │
        Processing Failed                 Processing Failed
                │                                 │
                ▼                                 ▼

       ┌──────────────────┐              ┌──────────────────┐
       │ Email DLQ        │              │ DB DLQ           │
       │------------------│              │------------------│
       │ Failed Emails    │              │ Failed DB Writes │
       │ Retry Analysis   │              │ Manual Recovery  │
       └──────────────────┘              └──────────────────┘
```
