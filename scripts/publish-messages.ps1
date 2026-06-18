<#
    Publica los mensajes de prueba en RabbitMQ usando la API de administracion.
    Equivalente a publish-messages.sh pero para Windows: no requiere bash ni jq,
    solo PowerShell (incluido en Windows).

    Uso:  powershell -ExecutionPolicy Bypass -File scripts\publish-messages.ps1
#>

$ErrorActionPreference = "Stop"
$api      = "http://localhost:15672/api/exchanges/%2F/campus.exchange/publish"
$pair     = [Text.Encoding]::ASCII.GetBytes("guest:guest")
$headers  = @{ Authorization = "Basic " + [Convert]::ToBase64String($pair) }

function Publish-Message($label, $payloadObject) {
    $payload = $payloadObject | ConvertTo-Json -Compress
    $body = @{
        properties       = @{ content_type = "application/json" }
        routing_key      = "campus.requests.in"
        payload          = $payload
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 5

    $resp = Invoke-RestMethod -Uri $api -Method Post -Headers $headers `
        -ContentType "application/json" -Body $body
    Write-Host ("  [{0,-9}] routed={1}  {2}" -f $label, $resp.routed, $payload)
}

Write-Host "Publicando mensajes de prueba en campus.requests.in..."

Publish-Message "ADMISSION" ([ordered]@{ request_id="REQ-1001"; student_name="Ana Perez";     student_document="1712345678"; request_type="ADMISSION"; channel="web";            created_at="2026-06-10T10:30:00" })
Publish-Message "PAYMENT"   ([ordered]@{ request_id="REQ-1002"; student_name="Luis Gomez";    student_document="1722222222"; request_type="PAYMENT";   channel="mobile";         created_at="2026-06-10T11:00:00" })
Publish-Message "SUPPORT"   ([ordered]@{ request_id="REQ-1003"; student_name="Carla Torres";  student_document="1733333333"; request_type="SUPPORT";   channel="admin-platform"; created_at="2026-06-10T11:30:00" })
Publish-Message "ACADEMIC"  ([ordered]@{ request_id="REQ-1004"; student_name="Pedro Morales"; student_document="1744444444"; request_type="ACADEMIC";  channel="web";            created_at="2026-06-10T12:00:00" })
Publish-Message "NO-RECON"  ([ordered]@{ request_id="REQ-1005"; student_name="Maria Sanchez"; student_document="1755555555"; request_type="LIBRARY";   channel="web";            created_at="2026-06-10T12:30:00" })
Publish-Message "INVALIDO"  ([ordered]@{ request_id="REQ-1006"; student_name="Diego Ruiz";    channel="web" })

Write-Host ""
Write-Host "Listo. Revisa las colas en http://localhost:15672 (Queues and Streams)."
