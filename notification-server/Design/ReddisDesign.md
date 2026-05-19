# Redis Internal Processing Architecture


                              ┌──────────────────────┐
                              │    Kafka Consumer    │
                              │----------------------│
                              │ Reads User Events    │
                              │ from Kafka Topic     │
                              └──────────┬───────────┘
                                         │
                                         │ Consume Event
                                         ▼

                     ┌──────────────────────────────────────┐
                     │                Redis                 │
                     │──────────────────────────────────────│
                     │                                      │
                     │  Intent Score Store                  │
                     │  Traffic Analytics Store             │
                     │  Session Tracking                    │
                     │  Sliding Window Counters             │
                     │  Real-Time Aggregation               │
                     │                                      │
                     └─────────────────┬────────────────────┘
                                       │
             ┌─────────────────────────┼─────────────────────────┐
             │                         │                         │
             │                         │                         │
             ▼                         ▼                         ▼

 ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
 │ Intent Scoring      │   │ Traffic Analytics   │   │ Session Tracking    │
 │---------------------│   │---------------------│   │---------------------│
 │ INCR user score     │   │ Page Hits           │   │ Active Users        │
 │ Activity Weighting  │   │ Event Counters      │   │ User Sessions       │
 │ Engagement Tracking │   │ Geo Analytics       │   │ TTL Expiration      │
 └──────────┬──────────┘   │ Real-Time Metrics   │   └──────────┬──────────┘
            │              └──────────┬──────────┘              │
            │                         │                         │
            ▼                         ▼                         ▼

 ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
 │ Threshold Checker   │   │ Analytics Dashboard │   │ Expiry Cleanup      │
 │---------------------│   │---------------------│   │---------------------│
 │ score >= threshold? │   │ Live Monitoring     │   │ Auto Key Removal    │
 │ Trigger Notification│   │ Traffic Graphs      │   │ Session Cleanup     │
 └──────────┬──────────┘   │ Real-Time Insights  │   └─────────────────────┘
            │              └─────────────────────┘
            │
            ▼

 ┌──────────────────────────────────────┐
 │ Notification Builder Service         │
 │--------------------------------------│
 │ Build Push / Email / SMS Payload     │
 └─────────────────┬────────────────────┘
                   │
                   ▼

 ┌──────────────────────────────────────┐
 │              RabbitMQ                │
 │--------------------------------------│
 │ Push Queue                           │
 │ Email Queue                          │
 │ SMS Queue                            │
 └──────────────────────────────────────┘