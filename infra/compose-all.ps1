<#
.SYNOPSIS
    Manages infrastructure services via Docker Compose.
.DESCRIPTION
    Runs 'docker compose' commands inside the infra folders for Kafka, RabbitMQ, and Redis.
.EXAMPLE
    .\compose-all.ps1 up
    .\compose-all.ps1 down
#>

# 1. Resolve repo root as the parent directory of this script's folder
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Get-Item (Join-Path $ScriptDir "..")).FullName

# 2. Capture the command (up/down) and any extra arguments
$Cmd = if ($args.Count -ge 1) { $args[0] } else { $null }
$ExtraArgs = if ($args.Count -gt 1) {
    @($args[1..($args.Count - 1)] | Where-Object { $_ -ne $null -and $_ -ne "" })
} else {
    @()
}

# Helper function to display usage instructions
function Show-Usage {
    Write-Host "Usage:" -ForegroundColor Cyan
    Write-Host "  .\compose-all.ps1 up"
    Write-Host "  .\compose-all.ps1 down"
    Write-Host "`nNotes:"
    Write-Host "  - Runs docker compose in: infra\kafka, infra\rabbitmq, infra\redis, infra\postgres"
    exit 2
}

# Validate that a command was provided
if ([string]::IsNullOrEmpty($Cmd)) {
    Show-Usage
}

# Helper function to navigate and run docker compose
function Invoke-Compose {
    param (
        [string]$ServiceDir,
        [string]$Action,
        [string[]]$AdditionalArgs
    )
    
    $TargetDir = Join-Path $RepoRoot "infra\$ServiceDir"
    
    try {
        Push-Location $TargetDir -ErrorAction Stop

        # Standardize args into a clean string array for execution
        if ($Action -eq "up") {
            $FullArgs = @("up", "-d")
            if ($AdditionalArgs -and $AdditionalArgs.Count -gt 0) { $FullArgs += $AdditionalArgs }
            docker compose --file docker-compose.yml $FullArgs
        } else {
            $FullArgs = @("down")
            if ($AdditionalArgs -and $AdditionalArgs.Count -gt 0) { $FullArgs += $AdditionalArgs }
            docker compose --file docker-compose.yml $FullArgs
        }

        return $LASTEXITCODE
    }
    finally {
        Pop-Location -ErrorAction SilentlyContinue
    }
}

# 3. Dispatch logic based on the user's input command
switch -Exact ($Cmd.ToLower()) {
    "up" {
        Write-Host "Starting infrastructure services..." -ForegroundColor Green
        
        $Services = @("kafka", "rabbitmq", "redis", "postgres")
        foreach ($Service in $Services) {
            $Ec = Invoke-Compose -ServiceDir $Service -Action "up" -AdditionalArgs $ExtraArgs
            if ($Ec -ne 0) { exit $Ec }
        }
    }
    
    "down" {
        Write-Host "Stopping infrastructure services..." -ForegroundColor Yellow
        
        # Stops in reverse order: postgres, redis, rabbitmq, kafka
        $Services = @("postgres", "redis", "rabbitmq", "kafka")
        foreach ($Service in $Services) {
            Invoke-Compose -ServiceDir $Service -Action "down" -AdditionalArgs $ExtraArgs
        }
        exit 0
    }
    
    Default {
        Show-Usage
    }
}