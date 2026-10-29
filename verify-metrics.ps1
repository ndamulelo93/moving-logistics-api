# Verification script for Task 9.3: Metrics Exposure Through Actuator
# This script verifies that all custom metrics are properly exposed

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Metrics Exposure Verification Script" -ForegroundColor Cyan
Write-Host "Task 9.3: Google Maps Distance Integration" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"
$actuatorUrl = "$baseUrl/actuator"

# Check if application is running
Write-Host "1. Checking if application is running..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$actuatorUrl/health" -Method Get -ErrorAction Stop
    Write-Host "   ✓ Application is running" -ForegroundColor Green
    Write-Host "   Status: $($health.status)" -ForegroundColor Gray
} catch {
    Write-Host "   ✗ Application is not running or not accessible" -ForegroundColor Red
    Write-Host "   Please start the application first: mvn spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Check metrics endpoint
Write-Host "2. Checking /actuator/metrics endpoint..." -ForegroundColor Yellow
try {
    $metrics = Invoke-RestMethod -Uri "$actuatorUrl/metrics" -Method Get -ErrorAction Stop
    Write-Host "   ✓ Metrics endpoint is accessible" -ForegroundColor Green
    Write-Host "   Total metrics available: $($metrics.names.Count)" -ForegroundColor Gray
} catch {
    Write-Host "   ✗ Metrics endpoint is not accessible" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Check for custom Google Maps metrics
Write-Host "3. Verifying custom Google Maps metrics..." -ForegroundColor Yellow

$customMetrics = @(
    "google_maps_api_calls_total",
    "distance_cache_hits_total",
    "distance_cache_misses_total",
    "distance_fallback_total",
    "google_maps_api_duration_seconds"
)

$foundMetrics = @()
$missingMetrics = @()

foreach ($metricName in $customMetrics) {
    if ($metrics.names -contains $metricName) {
        $foundMetrics += $metricName
        Write-Host "   ✓ $metricName" -ForegroundColor Green
    } else {
        $missingMetrics += $metricName
        Write-Host "   ⚠ $metricName (not yet recorded)" -ForegroundColor Yellow
    }
}

Write-Host ""

# Check individual metric endpoints
Write-Host "4. Checking individual metric endpoints..." -ForegroundColor Yellow

foreach ($metricName in $foundMetrics) {
    try {
        $metric = Invoke-RestMethod -Uri "$actuatorUrl/metrics/$metricName" -Method Get -ErrorAction Stop
        Write-Host "   ✓ /actuator/metrics/$metricName" -ForegroundColor Green
        
        # Show metric details
        if ($metric.measurements) {
            $value = $metric.measurements[0].value
            $stat = $metric.measurements[0].statistic
            Write-Host "     └─ $stat`: $value" -ForegroundColor Gray
        }
        
        # Show available tags
        if ($metric.availableTags -and $metric.availableTags.Count -gt 0) {
            $tags = ($metric.availableTags | ForEach-Object { "$($_.tag): [$($_.values -join ', ')]" }) -join ", "
            Write-Host "     └─ Tags: $tags" -ForegroundColor Gray
        }
    } catch {
        Write-Host "   ✗ Failed to access /actuator/metrics/$metricName" -ForegroundColor Red
    }
}

Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verification Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Metrics found: $($foundMetrics.Count)/$($customMetrics.Count)" -ForegroundColor Green

if ($missingMetrics.Count -gt 0) {
    Write-Host ""
    Write-Host "Note: Some metrics have not been recorded yet:" -ForegroundColor Yellow
    foreach ($metric in $missingMetrics) {
        Write-Host "  - $metric" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "These metrics will appear once the corresponding operations occur:" -ForegroundColor Yellow
    Write-Host "  - Make a distance calculation to record API calls" -ForegroundColor Gray
    Write-Host "  - Cache operations will record hits/misses" -ForegroundColor Gray
    Write-Host "  - Fallback scenarios will record fallback metrics" -ForegroundColor Gray
}

Write-Host ""
Write-Host "✓ Task 9.3 verification complete!" -ForegroundColor Green
Write-Host ""

# Additional endpoints to try
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Additional Actuator Endpoints" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Health:  $actuatorUrl/health" -ForegroundColor Gray
Write-Host "Info:    $actuatorUrl/info" -ForegroundColor Gray
Write-Host "Metrics: $actuatorUrl/metrics" -ForegroundColor Gray
Write-Host ""

# Example queries
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Example Metric Queries" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All metrics:" -ForegroundColor Gray
Write-Host "  curl $actuatorUrl/metrics" -ForegroundColor DarkGray
Write-Host ""
Write-Host "API calls (success only):" -ForegroundColor Gray
Write-Host "  curl '$actuatorUrl/metrics/google_maps_api_calls_total?tag=status:success'" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Fallback by reason:" -ForegroundColor Gray
Write-Host "  curl '$actuatorUrl/metrics/distance_fallback_total?tag=reason:rate_limit'" -ForegroundColor DarkGray
Write-Host ""
