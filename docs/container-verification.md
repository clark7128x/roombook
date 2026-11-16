# T-27: контейнерна перевірка

05.11.2026. Локальний Docker Desktop, production-профіль.

```json
{
  "result": "PASS",
  "image": "roombook:verify",
  "seconds": 32.9,
  "nonRoot": true,
  "readOnly": true,
  "memoryBytes": 1073741824,
  "cpus": 2,
  "checks": [
    "empty DB migrations",
    "production role setup",
    "bootstrap",
    "login",
    "secure cookie",
    "Ukrainian ordering",
    "occupancy",
    "security headers",
    "readiness"
  ],
  "boundary": "Local isolated Docker network; TLS reverse proxy and office-network restrictions are not exercised."
}
```

Runtime: Temurin 21.0.13+9. Base digest: `sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269`.

CI workflow створено; його віддалений запуск у GitHub не виконувався. TLS-проксі, офісні мережеві обмеження та production-секрети потребують перевірки оператором.
