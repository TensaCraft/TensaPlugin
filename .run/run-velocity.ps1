param(
    [switch]$SkipBuild,
    [switch]$WithTests,
    [string]$VelocityVersion,
    [int]$Port = 25577
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeRoot = Join-Path $projectRoot ".run\velocity"
$pluginsDir = Join-Path $runtimeRoot "plugins"
$resolvedJava = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
$javaCmd = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME "bin\java.exe"
} elseif ($resolvedJava) {
    $resolvedJava
} else {
    "java"
}

function Get-ProjectMetadata {
    [xml]$pom = Get-Content (Join-Path $projectRoot "pom.xml")
    $finalName = $pom.project.build.finalName
    if ([string]::IsNullOrWhiteSpace($finalName)) {
        $finalName = $pom.project.artifactId
    }

    [pscustomobject]@{
        FinalName = $finalName
        JavaVersion = [int]$pom.project.properties.'java.version'
        VelocityVersion = [string]$pom.project.properties.'velocity.api.version'
    }
}

$project = Get-ProjectMetadata
$pluginJar = Join-Path $projectRoot ("target\" + $project.FinalName + ".jar")

function Invoke-Build {
    $args = @("package")
    if (-not $WithTests) {
        $args = @("-Pskip-tests", "package")
    }

    Write-Host "Building plugin..."
    & mvn @args
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed."
    }
}

function Get-LatestVelocityBuild {
    param(
        [string]$RequestedVersion
    )

    $headers = @{ "User-Agent" = "Tensa-DevRunner/1.0" }
    $projectInfo = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/velocity"
    [string[]]$availableVersions = $projectInfo.versions.'3.0.0'

    if (-not $availableVersions -or $availableVersions.Count -eq 0) {
        throw "Fill v3 did not return any Velocity 3.x versions."
    }

    $version = if ($RequestedVersion -and $availableVersions -contains $RequestedVersion) {
        $RequestedVersion
    } else {
        if ($RequestedVersion) {
            Write-Warning "Velocity version '$RequestedVersion' is not published via Fill v3. Falling back to the latest available Velocity 3.x build."
        }
        $availableVersions[0]
    }

    $builds = Invoke-RestMethod -Headers $headers -Uri "https://fill.papermc.io/v3/projects/velocity/versions/$([uri]::EscapeDataString($version))/builds"
    if (-not $builds -or $builds.Count -eq 0) {
        throw "No Velocity builds found for version $version."
    }

    $stableBuilds = @($builds | Where-Object { $_.channel -eq "STABLE" })
    $build = if ($stableBuilds.Count -gt 0) {
        $stableBuilds | Sort-Object id | Select-Object -Last 1
    } else {
        $builds | Sort-Object id | Select-Object -Last 1
    }

    $download = $build.downloads.'server:default'
    if (-not $download) {
        throw "Velocity build $($build.id) for version $version does not expose a server:default download."
    }

    return [pscustomobject]@{
        Version = $version
        Build = [int]$build.id
        FileName = [string]$download.name
        Sha256 = [string]$download.checksums.sha256
        Url = [string]$download.url
    }
}

function Ensure-FileHash {
    param(
        [string]$Path,
        [string]$ExpectedSha256
    )

    if (-not (Test-Path $Path)) {
        return $false
    }

    $actual = (Get-FileHash -Algorithm SHA256 -Path $Path).Hash.ToLowerInvariant()
    return $actual -eq $ExpectedSha256.ToLowerInvariant()
}

function Ensure-VelocityRuntime {
    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null

    $buildInfo = Get-LatestVelocityBuild -RequestedVersion $VelocityVersion
    $serverJar = Join-Path $runtimeRoot $buildInfo.FileName

    if (-not (Ensure-FileHash -Path $serverJar -ExpectedSha256 $buildInfo.Sha256)) {
        Write-Host "Downloading Velocity $($buildInfo.Version) build $($buildInfo.Build)..."
        Invoke-WebRequest -Uri $buildInfo.Url -OutFile $serverJar
        if (-not (Ensure-FileHash -Path $serverJar -ExpectedSha256 $buildInfo.Sha256)) {
            throw "Downloaded Velocity jar failed SHA256 verification."
        }
    }

    return $serverJar
}

function Ensure-DevConfig {
    $velocityToml = Join-Path $runtimeRoot "velocity.toml"
    $forwardingSecret = Join-Path $runtimeRoot "forwarding.secret"

    if (-not (Test-Path $forwardingSecret)) {
        [guid]::NewGuid().ToString("N") | Set-Content -Path $forwardingSecret -NoNewline -Encoding UTF8
    }

    if (-not (Test-Path $velocityToml)) {
        @"
config-version = "2.7"
bind = "127.0.0.1:$Port"
motd = "<green>Tensa Dev Proxy</green>"
show-max-players = 100
online-mode = false
force-key-authentication = false
player-info-forwarding-mode = "none"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "disabled"
log-command-executions = true
log-player-connections = true
try = [ "lobby" ]

[servers]
lobby = "127.0.0.1:25566"
"@ | Set-Content -Path $velocityToml -Encoding UTF8
    }
}

function Sync-PluginJar {
    param(
        [string]$SourceJar
    )

    $destinationJar = Join-Path $pluginsDir ($project.FinalName + ".jar")
    $legacyJar = Join-Path $pluginsDir "TENSA.jar"

    if ((Test-Path $legacyJar) -and ($legacyJar -ne $destinationJar)) {
        Remove-Item -Path $legacyJar -Force
    }

    Copy-Item -Path $SourceJar -Destination $destinationJar -Force
    return $destinationJar
}

function Test-JavaVersion {
    $stdoutPath = [System.IO.Path]::GetTempFileName()
    $stderrPath = [System.IO.Path]::GetTempFileName()
    try {
        $process = Start-Process -FilePath $javaCmd `
            -ArgumentList "-version" `
            -NoNewWindow `
            -Wait `
            -PassThru `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath

        $versionLines = @()
        if (Test-Path $stdoutPath) {
            $versionLines += Get-Content $stdoutPath
        }
        if (Test-Path $stderrPath) {
            $versionLines += Get-Content $stderrPath
        }

        $versionOutput = $versionLines | Select-Object -First 1
        $javaExitCode = $process.ExitCode
    } finally {
        Remove-Item $stdoutPath, $stderrPath -ErrorAction SilentlyContinue
    }

    if ($javaExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($versionOutput)) {
        throw "Unable to start Java from '$javaCmd'."
    }

    if ($versionOutput -match 'version "(\d+)(?:\.(\d+))?') {
        $detected = [int]$matches[1]
        if ($detected -lt $project.JavaVersion) {
            throw "Java $($project.JavaVersion)+ is required, but '$versionOutput' was detected."
        }
    }
}

if (-not $SkipBuild) {
    Invoke-Build
}

if (-not (Test-Path $pluginJar)) {
    throw "Built plugin jar not found at $pluginJar."
}

Test-JavaVersion
$requestedVelocityVersion = if ($VelocityVersion) { $VelocityVersion } else { $project.VelocityVersion }
$serverJar = Ensure-VelocityRuntime -RequestedVersion $requestedVelocityVersion
Ensure-DevConfig
$installedPluginJar = Sync-PluginJar -SourceJar $pluginJar

Push-Location $runtimeRoot
try {
    Write-Host "Starting Velocity from $serverJar with plugin $installedPluginJar"
    & $javaCmd "-jar" $serverJar
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
