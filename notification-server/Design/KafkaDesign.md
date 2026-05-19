# Notification Pipeline Architecture


                              ┌──────────────────────┐
                              │   Event Producers    │
                              │----------------------│
                              │ User Actions         │
                              │ Click Events         │
                              │ Purchases            │
                              │ Activity Streams     │
                              └──────────┬───────────┘
                                         │
                                         │ Publish Events
                                         ▼
                         ┌────────────────────────────────┐
                         │             Kafka              │
                         │────────────────────────────────│
                         │ Broker-1                      │
                         │                                │
                         │ Topic: user-events             │
                         │ ┌────────────────────────────┐ │
                         │ │        Partition-0         │ │
                         │ │----------------------------│ │
                         │ │ Event-1                    │ │
                         │ │ Event-2                    │ │
                         │ │ Event-3                    │ │
                         │ │ Event-4                    │ │
                         │ └────────────────────────────┘ │
                         └──────────────┬─────────────────┘
                                        │
                    ┌───────────────────┴────────────────────┐
                    │                                        │
                    │                                        │
                    ▼                                        ▼

        ┌──────────────────────────┐          ┌──────────────────────────┐
        │ Consumer Group - A       │          │ Consumer Group - B       │
        │--------------------------│          │--------------------------│
        │ Event Log Consumer       │          │ Intent Score Consumer    │
        └─────────────┬────────────┘          └─────────────┬────────────┘
                      │                                     │
                      │ Batch Processing                    │ Real-Time Processing
                      ▼                                     ▼

        ┌──────────────────────────┐          ┌──────────────────────────┐
        │ Batch Aggregator         │          │ Redis Intent Store       │
        │--------------------------│          │--------------------------│
        │ Collect Events           │          │ user:123:intent=20       │
        │ Buffer Records           │          │ user:456:intent=50       │
        │ Bulk Insert              │          │ user:789:intent=80       │
        └─────────────┬────────────┘          └─────────────┬────────────┘
                      │                                     │
                      ▼                                     │
        ┌──────────────────────────┐                        │
        │ PostgreSQL / DB          │                        │
        │--------------------------│                        │
        │ Persistent Event Logs    │                        │
        │ Analytics                │                        │
        │ Audit Trail              │                        │
        └──────────────────────────┘                        │
                                                            │
                                                            │
                                                            ▼
                                         ┌──────────────────────────────┐
                                         │ Intent Threshold Checker     │
                                         │------------------------------│
                                         │ score >= threshold ?         │
                                         │                              │
                                         │ YES → Build Notification     │
                                         └──────────────┬───────────────┘
                                                        │
                                                        ▼

                                    ┌────────────────────────────────┐
                                    │ Notification Builder Service   │
                                    │--------------------------------│
                                    │ Build Push Payload             │
                                    │ Build Email Payload            │
                                    │ Build SMS Payload              │
                                    │ Template Rendering             │
                                    └──────────────┬─────────────────┘
                                                   │
                                                   ▼

                                ┌────────────────────────────────────┐
                                │             RabbitMQ               │
                                │------------------------------------│
                                │ Exchange                           │
                                │   ├── Push Queue                   │
                                │   ├── Email Queue                  │
                                │   └── SMS Queue                    │
                                └────────────────────────────────────┘