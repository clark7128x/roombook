# T-28: копіювання, відновлення та моніторинг

Перевірено 05.11.2026.

## Відновлення

```json
{
  "result": "PASS",
  "secondsToReady": 60.77,
  "encryptedBytes": 16727,
  "audits": [
    "inv-07-cancel-state.sql",
    "inv-08-no-overlap.sql",
    "inv-09-inactive-room-bookings.sql",
    "inv-10-foreign-keys.sql",
    "inv-14-admin-count.sql"
  ],
  "sourceRole": "roombook_backup",
  "encryption": "OpenPGP AES256 symmetric",
  "readiness": "UP",
  "boundary": "Separate fresh PostgreSQL container on the same local Docker host; production storage transfer and physical-host recovery remain unverified."
}
```

## Трихвилинна недоступність БД

```json
{
  "samples": [
    {
      "at": "2026-11-05T09:23:00+00:00",
      "ready": false,
      "login": true
    },
    {
      "at": "2026-11-05T09:24:00+00:00",
      "ready": false,
      "login": true
    },
    {
      "at": "2026-11-05T09:25:00+00:00",
      "ready": false,
      "login": true
    }
  ],
  "dbUnavailableSeconds": 180.5,
  "result": {
    "alerts": [
      "UNAVAILABLE_3_MINUTES",
      "BACKUP_OVER_26_HOURS",
      "BACKUP_FAILED",
      "DISK_OVER_80_PERCENT"
    ],
    "observedMinutes": 3,
    "windowMinutes": 3626,
    "missingMinutes": 3623,
    "availableMinutes": 0,
    "monthToDateAvailabilityPercent": 0.0
  },
  "windowExercised": true,
  "recovery": "UP",
  "delivery": "Alert decisions verified locally; no external notification sent."
}
```

`python ops/monitoring/test_rules.py`: 2 успішні перевірки — правила сповіщень, облік пропущених хвилин, поріг розміру копії та 30-копійна ротація.

Відновлено невеликий тестовий набір; RTO production-обсягу, зовнішня доставка сповіщень, off-host mount і захищене зберігання ключа потребують перевірки оператором. Нульова month-to-date доступність у звіті належить штучним трьом відмовам із рештою хвилин без вимірювань, а не production-системі.
