# moving-logistics-api
This is to enable client to get a quote and book a bakkie or truck based on the size of items they want to transport

## Table of Contents
- [Features](#features)
- [Google Maps Distance Matrix API Integration](#google-maps-distance-matrix-api-integration)
  - [Overview](#overview)
  - [Configuration](#configuration)
  - [Environment Variables](#environment-variables)
  - [Configuration Properties](#configuration-properties)
  - [Example Configurations](#example-configurations)
  - [Fallback Behavior](#fallback-behavior)
  - [Cost Optimization Tips](#cost-optimization-tips)
  - [Monitoring](#monitoring)

## Features
- Real-time transport quote calculation
- Vehicle booking management
- Integration with Google Maps for accurate distance calculations
- Zone-based fallback system for reliability
- Comprehensive caching to minimize API costs

## Google Maps Distance Matrix API Integration

### Overview

The system integrates with Google Maps Distance Matrix API to calculate accurate road distances between pickup and dropoff locations. This replaces basic zone-based distance calculations with real-world driving distances, providing more accurate quotes to customers.

**Key Features:**
- Real-time distance calculation using actual road routes
- Intelligent caching to minimize API costs (7-day default TTL)
- Automatic fallback to zone-based calculation during API failures
- Rate limiting to control costs
- Full backward compatibility with existing suburb-based quotes

### Configuration

#### Environment Variables

The Google Maps API key must be configured via environment variable for security:

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `GOOGLE_MAPS_API_KEY` | Yes (when enabled) | Your Google Maps API key with Distance Matrix API enabled | `AIzaSyC-Abc123...` |

**Setting the environment variable:**

**Linux/macOS:**
```bash
export GOOGLE_MAPS_API_KEY="your-api-key-here"
```

**Windows (PowerShell):**
```powershell
$env:GOOGLE_MAPS_API_KEY="your-api-key-here"
```

**Docker / Docker Compose:**
```yaml
environment:
  - GOOGLE_MAPS_API_KEY=your-api-key-here
```

**Important:** Never commit API keys to source control. Always use environment variables or secrets management.

#### Configuration Properties

All Google Maps integration settings are under the `google.maps` namespace in `application.yml`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `google.maps.api-key` | String | _(required)_ | Google Maps API key (use `${GOOGLE_MAPS_API_KEY}` to read from environment) |
| `google.maps.enabled` | Boolean | `true` | Enable/disable Google Maps integration. When `false`, uses zone-based fallback for all requests |
| `google.maps.timeout-seconds` | Integer | `5` | API request timeout in seconds. Increase for slower networks |
| `google.maps.max-requests-per-minute` | Integer | `100` | Maximum API requests per minute. Exceeding this triggers automatic fallback |
| `google.maps.cache-enabled` | Boolean | `true` | Enable/disable response caching. Highly recommended to keep enabled |
| `google.maps.cache-ttl-days` | Integer | `7` | How long cached distances remain valid (in days) |

### Example Configurations

#### Development Environment

For local development, you can use relaxed settings:

```yaml
google:
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY:}
    enabled: true
    timeout-seconds: 10  # Longer timeout for debugging
    max-requests-per-minute: 100
    cache-enabled: true
    cache-ttl-days: 7
```

**Disabling API in Development:**

To avoid using API quota during development, set:
```yaml
google:
  maps:
    enabled: false  # Uses zone-based fallback for all requests
```

#### Production Environment

For production, use stricter settings with proper monitoring:

```yaml
google:
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY}  # Must be set via environment variable
    enabled: true
    timeout-seconds: 5  # Fail fast
    max-requests-per-minute: 100  # Adjust based on your API quota
    cache-enabled: true
    cache-ttl-days: 7
```

#### Testing Environment

For automated tests, disable the integration:

**`application-test.yml`:**
```yaml
google:
  maps:
    enabled: false  # Never call real API in tests
    cache-enabled: false
```

### Fallback Behavior

The system automatically falls back to zone-based distance calculation in the following scenarios:

| Scenario | Fallback Trigger | Behavior |
|----------|-----------------|----------|
| **API Disabled** | `google.maps.enabled=false` | All requests use zone-based calculation |
| **Rate Limit Exceeded** | More than `max-requests-per-minute` | Subsequent requests in that minute use zone fallback |
| **API Connection Failure** | Network timeout or connection error | Falls back to zone calculation, logs warning |
| **API Authentication Error** | Invalid or missing API key | Falls back to zone calculation, logs error |
| **Address Not Found** | Google Maps cannot geocode address | Attempts zone calculation using suburb |
| **API Unavailable** | Google Maps API returns error | Falls back to zone calculation |

**Fallback Process:**
1. System attempts Google Maps API call
2. On failure, extracts suburb from address
3. Performs zone-based distance lookup
4. Logs warning with fallback reason
5. Returns fallback distance to maintain service availability

**Zone-Based Fallback Requirements:**
- Pickup and dropoff suburbs must exist in the `suburb_zone` table
- Zone pair must have a defined distance in the `zone_distance` table
- If zone lookup also fails, returns `InvalidAddressException`

### Cost Optimization Tips

Google Maps Distance Matrix API charges per request. Follow these best practices to minimize costs:

#### 1. **Enable Caching (Highly Recommended)**
```yaml
google:
  maps:
    cache-enabled: true
    cache-ttl-days: 7  # Adjust based on your needs
```
- Caching prevents duplicate API calls for the same route
- Bidirectional caching: `(A → B)` and `(B → A)` use the same cache entry
- Database-backed cache survives application restarts
- Stale entries (>30 days unaccessed) are automatically cleaned up

#### 2. **Configure Rate Limiting**
```yaml
google:
  maps:
    max-requests-per-minute: 100  # Adjust to your quota
```
- Prevents accidental quota exhaustion
- Automatically uses zone fallback when limit exceeded
- No service degradation during rate limiting

#### 3. **Disable in Non-Production Environments**
```yaml
# application-dev.yml
google:
  maps:
    enabled: false  # Free zone-based calculation
```
- Use zone fallback in development/testing environments
- Only enable API in staging/production where accuracy is critical

#### 4. **Monitor API Usage**

The system exposes metrics via Spring Boot Actuator:

```bash
# Check API call count
curl http://localhost:8080/actuator/metrics/google_maps_api_calls_total

# Check cache hit rate
curl http://localhost:8080/actuator/metrics/distance_cache_hits_total
curl http://localhost:8080/actuator/metrics/distance_cache_misses_total

# Check fallback usage
curl http://localhost:8080/actuator/metrics/distance_fallback_total
```

**High cache hit rate (>80%) indicates good cost optimization.**

#### 5. **Adjust Cache TTL Based on Usage Patterns**

- **High-traffic routes:** Increase TTL to 14-30 days
- **Low-traffic routes:** Keep at 7 days to ensure freshness
- **Static routes:** Consider longer TTL (30+ days)

```yaml
google:
  maps:
    cache-ttl-days: 14  # Longer TTL = fewer API calls
```

#### 6. **Set Appropriate Quotas in Google Cloud Console**

- Configure daily/monthly quotas in Google Cloud Console
- Set budget alerts to monitor spending
- Use separate API keys for different environments

### Monitoring

#### Available Metrics

The system exposes the following metrics through `/actuator/metrics`:

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `google_maps_api_calls_total` | Counter | `status` | Total API calls (success/failure) |
| `distance_cache_hits_total` | Counter | - | Number of cache hits |
| `distance_cache_misses_total` | Counter | - | Number of cache misses |
| `distance_fallback_total` | Counter | `reason` | Fallback invocations by reason |
| `google_maps_api_duration_seconds` | Histogram | - | API response time distribution |
| `distance_cache_size` | Gauge | - | Current number of cached entries |

#### Health Checks

The application health endpoint reports overall status:

```bash
curl http://localhost:8080/actuator/health
```

#### Log Monitoring

Key log patterns to monitor:

```
INFO  - Google Maps API call succeeded: pickup=..., dropoff=..., distance=...km
WARN  - Falling back to zone-based distance: pickup=..., dropoff=..., reason=...
ERROR - Google Maps API call failed: ...
```

#### Setting Up Alerts

Recommended alerts based on metrics:

1. **High Fallback Rate:** Alert if `distance_fallback_total` exceeds threshold
2. **Low Cache Hit Rate:** Alert if cache hit rate drops below 70%
3. **API Errors:** Alert on `google_maps_api_calls_total{status="failure"}` spikes
4. **Rate Limit Hits:** Alert when rate limiting frequently triggered

---

## Getting Started

(Additional setup instructions would go here...)
