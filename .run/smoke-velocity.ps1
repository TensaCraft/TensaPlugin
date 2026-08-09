param(
    [switch]$WithTests,
    [switch]$SkipBuild,
    [string]$JavaHome,
    [string]$VelocityVersion,
    [int]$Port = 25578
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeName = "velocity-smoke"
$runtimeRoot = Join-Path $PSScriptRoot $runtimeName
$pluginData = Join-Path $runtimeRoot "plugins\tensa"
$runner = Join-Path $PSScriptRoot "run-velocity.ps1"
$transcript = [System.Text.StringBuilder]::new()

function Resolve-JavaHome {
    if ($JavaHome) {
        return [System.IO.Path]::GetFullPath($JavaHome)
    }

    $candidates = @()
    if ($env:TENSA_JAVA_HOME) {
        $candidates += $env:TENSA_JAVA_HOME
    }
    $candidates += "C:\Program Files\Java\jdk-25"
    $userJdks = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path -LiteralPath $userJdks) {
        $candidates += Get-ChildItem -LiteralPath $userJdks -Directory -Filter "*25*" |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "bin\java.exe"))) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }
    throw "Java 25 was not found. Pass -JavaHome with a JDK 25 directory."
}

function Write-Utf8File {
    param([string]$Path, [string]$Content)
    $parent = Split-Path $Path -Parent
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function New-SmokeConfiguration {
    $rootConfig = @'
language: uk
use_uuid: true
modules:
  proxy-bridge: false
  librelogin-auth-bridge: false
  scheduler: true
  communications: true
  rcon-manager: false
  rcon-server: false
  request-module: false
  player-time: false
  command-queue: false
  text-reader: false
database:
  enable: false
storage:
  type: local
  local_file: storage/smoke-users
user_meta:
  default_persist: true
velocity:
  log_cleanup:
    enable: false
'@
    $schedulerConfig = @'
config_version: 1
tasks:
  smoke-tick:
    enabled: true
    initial_delay: 1s
    interval: 2s
    mode: all
    commands:
      - "tparse SMOKE_SCHED_TICK"
'@
    Write-Utf8File -Path (Join-Path $pluginData "config.yml") -Content $rootConfig
    Write-Utf8File -Path (Join-Path $pluginData "scheduler\config.yml") -Content $schedulerConfig
    Write-Utf8File -Path (Join-Path $runtimeRoot "plugins\bStats\config.txt") -Content @'
enabled=false
server-uuid=00000000-0000-0000-0000-000000000000
log-errors=false
log-sent-data=false
log-response-status-text=false
'@
}

function Add-TranscriptLine {
    param([string]$Line, [string]$Stream)
    if ($null -eq $Line) {
        return
    }
    [void]$transcript.AppendLine($Line)
    if ($Stream -eq "stderr") {
        Write-Host $Line -ForegroundColor DarkYellow
    } else {
        Write-Host $Line
    }
}

function Read-ProcessOutput {
    param([hashtable]$Readers, [int]$WaitMilliseconds = 0)
    $deadline = [DateTime]::UtcNow.AddMilliseconds($WaitMilliseconds)
    do {
        $madeProgress = $false
        foreach ($name in @("stdout", "stderr")) {
            $state = $Readers[$name]
            if ($null -eq $state.Task -or -not $state.Task.IsCompleted) {
                continue
            }
            $line = $state.Task.GetAwaiter().GetResult()
            if ($null -eq $line) {
                $state.Task = $null
            } else {
                Add-TranscriptLine -Line $line -Stream $name
                $state.Task = $state.Stream.ReadLineAsync()
            }
            $madeProgress = $true
        }
        if (-not $madeProgress -and [DateTime]::UtcNow -lt $deadline) {
            Start-Sleep -Milliseconds 25
        }
    } while ([DateTime]::UtcNow -lt $deadline)
}

function Wait-ForOutput {
    param(
        [System.Diagnostics.Process]$Process,
        [hashtable]$Readers,
        [string]$Pattern,
        [int]$TimeoutSeconds,
        [int]$MinimumMatches = 1
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Read-ProcessOutput -Readers $Readers -WaitMilliseconds 100
        $matches = [regex]::Matches($transcript.ToString(), $Pattern,
                [System.Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
        if ($matches -ge $MinimumMatches) {
            return
        }
        if ($Process.HasExited) {
            throw "Velocity exited before output '$Pattern' appeared (exit $($Process.ExitCode))."
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for output '$Pattern' ($MinimumMatches match(es))."
}

function Send-ConsoleCommand {
    param([System.Diagnostics.Process]$Process, [string]$Command)
    if ($Process.HasExited) {
        throw "Velocity exited before command '$Command' could be sent."
    }
    $Process.StandardInput.WriteLine($Command)
    $Process.StandardInput.Flush()
}

function Assert-SmokeFiles {
    $required = @(
        (Join-Path $pluginData "config.yml"),
        (Join-Path $pluginData "scheduler\config.yml"),
        (Join-Path $pluginData "communications\discord.yml"),
        (Join-Path $pluginData "communications\chats.yml"),
        (Join-Path $pluginData "lang\uk.yml")
    )
    foreach ($path in $required) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Expected runtime file was not generated: $path"
        }
    }
    foreach ($name in @("discord.yml", "chats.yml")) {
        $content = [System.IO.File]::ReadAllText((Join-Path $pluginData "communications\$name"))
        if ($content -notmatch '(?m)^config_version:\s*2\s*$') {
            throw "$name was not generated with config_version 2."
        }
        if ($content -match '(?im)^\s*(?:token|chat_url|events_url):\s*\S+') {
            throw "$name unexpectedly contains a generated credential value."
        }
    }

    $ukBytes = [System.IO.File]::ReadAllBytes((Join-Path $pluginData "lang\uk.yml"))
    $strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
    $ukText = $strictUtf8.GetString($ukBytes)
    if (-not $ukText.Contains("увімкнено") -or $ukText.Contains([char]0xFFFD)) {
        throw "Runtime Ukrainian localization was not synchronized as valid UTF-8."
    }
}

$resolvedJavaHome = Resolve-JavaHome
$prepareArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $runner,
    "-RuntimeName", $runtimeName,
    "-Port", $Port,
    "-JavaHome", $resolvedJavaHome,
    "-ResetRuntime",
    "-PrepareOnly"
)
if ($WithTests) {
    $prepareArgs += "-WithTests"
}
if ($SkipBuild) {
    $prepareArgs += "-SkipBuild"
}
if ($VelocityVersion) {
    $prepareArgs += @("-VelocityVersion", $VelocityVersion)
}

$pwsh = Get-Command pwsh.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
if (-not $pwsh) {
    $pwsh = Get-Command powershell.exe -ErrorAction Stop | Select-Object -First 1 -ExpandProperty Source
}

Write-Host "Preparing clean local Velocity smoke runtime..."
& $pwsh @prepareArgs
if ($LASTEXITCODE -ne 0) {
    throw "Velocity smoke runtime preparation failed."
}

New-SmokeConfiguration
$serverJar = Get-ChildItem -LiteralPath $runtimeRoot -File -Filter "velocity-*.jar" |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (-not $serverJar) {
    throw "Prepared Velocity jar was not found."
}
$pluginJar = Get-ChildItem -LiteralPath (Join-Path $runtimeRoot "plugins") -File -Filter "Tensa.jar" |
        Select-Object -First 1
if (-not $pluginJar) {
    throw "Prepared Tensa plugin jar was not found."
}

$javaExe = Join-Path $resolvedJavaHome "bin\java.exe"
$javacExe = Join-Path $resolvedJavaHome "bin\javac.exe"
$probeSource = Join-Path $PSScriptRoot "Velocity4TextPipelineProbe.java"
$probeClasses = Join-Path $runtimeRoot "probe-classes"
[System.IO.Directory]::CreateDirectory($probeClasses) | Out-Null
& $javacExe -encoding UTF-8 -d $probeClasses -cp "$($serverJar.FullName);$($pluginJar.FullName)" $probeSource
if ($LASTEXITCODE -ne 0) {
    throw "Velocity 4 text compatibility probe did not compile."
}
$probeOutput = & $javaExe -cp "$($serverJar.FullName);$($pluginJar.FullName);$probeClasses" Velocity4TextPipelineProbe 2>&1
if ($LASTEXITCODE -ne 0 -or $probeOutput -notcontains "VELOCITY_TEXT_PIPELINE_COMPATIBLE") {
    $probeOutput | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    throw "Built Tensa jar is binary-incompatible with Velocity 4 text events."
}
Write-Host "Velocity 4 text compatibility probe PASSED."

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $javaExe
$startInfo.WorkingDirectory = $runtimeRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.StandardInputEncoding = [System.Text.UTF8Encoding]::new($false)
$startInfo.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
$startInfo.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
[void]$startInfo.ArgumentList.Add("-Dterminal.ansi=false")
[void]$startInfo.ArgumentList.Add("-Dfile.encoding=UTF-8")
[void]$startInfo.ArgumentList.Add("--enable-native-access=ALL-UNNAMED")
[void]$startInfo.ArgumentList.Add("-jar")
[void]$startInfo.ArgumentList.Add($serverJar.FullName)
foreach ($secretName in @(
    "TENSA_DISCORD_BOT_TOKEN",
    "TENSA_DISCORD_WEBHOOK_URL",
    "TENSA_DISCORD_EVENTS_WEBHOOK_URL"
)) {
    [void]$startInfo.Environment.Remove($secretName)
}

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$readers = $null
$shutdownSent = $false
try {
    Write-Host "Starting isolated Velocity on 127.0.0.1:$Port..."
    if (-not $process.Start()) {
        throw "Velocity process did not start."
    }
    $readers = @{
        stdout = @{ Stream = $process.StandardOutput; Task = $process.StandardOutput.ReadLineAsync() }
        stderr = @{ Stream = $process.StandardError; Task = $process.StandardError.ReadLineAsync() }
    }

    Wait-ForOutput -Process $process -Readers $readers -Pattern 'Done \(' -TimeoutSeconds 45
    Wait-ForOutput -Process $process -Readers $readers -Pattern 'Command Scheduler.*ENABLED' -TimeoutSeconds 10
    Wait-ForOutput -Process $process -Readers $readers -Pattern 'Communications.*ENABLED' -TimeoutSeconds 10
    Assert-SmokeFiles

    Send-ConsoleCommand -Process $process -Command "tensamodules"
    Send-ConsoleCommand -Process $process -Command "tensainfo communications"
    Wait-ForOutput -Process $process -Readers $readers -Pattern 'runtime=chat-only.*jda=unavailable.*slash=unavailable' -TimeoutSeconds 10

    $renderOutputStart = $transcript.Length
    $renderTemplate = "tparse <aqua>★</aqua> <gold>{username}</gold> проголосував за сервер на <click:open_url:'https://minecraft-ua.com/minecraft/aeronautics'><aqua>https://minecraft-ua.com/minecraft/aeronautics</aqua></click> та отримав бонус!"
    Send-ConsoleCommand -Process $process -Command $renderTemplate
    Wait-ForOutput -Process $process -Readers $readers -Pattern '\{username\}.*https://minecraft-ua\.com/minecraft/aeronautics' -TimeoutSeconds 5
    $renderOutput = $transcript.ToString().Substring($renderOutputStart)
    $renderedLines = @($renderOutput -split '\r?\n' | Where-Object { $_ -match '\{username\}.*https://minecraft-ua\.com/minecraft/aeronautics' })
    if ($renderedLines.Count -ne 1 -or $renderedLines[0].Contains("click:open_url")) {
        throw "MiniMessage click tag leaked into visible runtime output."
    }

    $clickOutputStart = $transcript.Length
    $clickTemplate = "m TensaCraft <aqua>★</aqua> <gold>{username}</gold> проголосував за сервер на <click:open_url:'https://minecraft-ua.com/minecraft/aeronautics'><aqua>https://minecraft-ua.com/minecraft/aeronautics</aqua></click> та отримав бонус!"
    Send-ConsoleCommand -Process $process -Command $clickTemplate
    Read-ProcessOutput -Readers $readers -WaitMilliseconds 1500
    $clickOutput = $transcript.ToString().Substring($clickOutputStart)
    if ($clickOutput -match 'NoSuchFieldError|Unable to invoke command') {
        throw "Private-message MiniMessage rendering is binary-incompatible with this Velocity runtime."
    }

    Wait-ForOutput -Process $process -Readers $readers -Pattern 'SMOKE_SCHED_TICK' -TimeoutSeconds 8 -MinimumMatches 2

    $beforeReload = [regex]::Matches($transcript.ToString(), 'SMOKE_SCHED_TICK').Count
    for ($index = 0; $index -lt 5; $index++) {
        Send-ConsoleCommand -Process $process -Command "tensareload scheduler"
        Start-Sleep -Milliseconds 200
    }
    Wait-ForOutput -Process $process -Readers $readers -Pattern 'Command Scheduler.*RELOADED' -TimeoutSeconds 15 -MinimumMatches 5
    Send-ConsoleCommand -Process $process -Command "tparse SMOKE_RELOAD_BOUNDARY"
    Wait-ForOutput -Process $process -Readers $readers -Pattern 'SMOKE_RELOAD_BOUNDARY' -TimeoutSeconds 5
    Read-ProcessOutput -Readers $readers -WaitMilliseconds 6500

    $afterReload = [regex]::Matches($transcript.ToString(), 'SMOKE_SCHED_TICK').Count - $beforeReload
    if ($afterReload -lt 2 -or $afterReload -gt 5) {
        throw "Scheduler produced $afterReload post-reload ticks; expected 2..5 from one active runtime."
    }

    $fatalPattern = '(?im)Enable failed for module|Failed to initialize a plugin|\[ERROR\]|Exception in thread|NoClassDefFoundError|LinkageError'
    if ([regex]::IsMatch($transcript.ToString(), $fatalPattern)) {
        throw "Velocity smoke log contains a fatal plugin/runtime signature."
    }

    Send-ConsoleCommand -Process $process -Command "shutdown"
    $shutdownSent = $true
    if (-not $process.WaitForExit(20000)) {
        throw "Velocity did not stop within 20 seconds."
    }
    Read-ProcessOutput -Readers $readers -WaitMilliseconds 500
    if ($process.ExitCode -ne 0) {
        throw "Velocity stopped with exit code $($process.ExitCode)."
    }
    if ($transcript.ToString() -notmatch 'Command Scheduler.*DISABLED') {
        throw "Scheduler did not report a clean shutdown."
    }

    Write-Host "Velocity 4 smoke test PASSED: binary text probe, clean startup, v2 config generation, diagnostics, formatting, scheduler execution, five targeted reloads, no duplicate runtime, clean shutdown." -ForegroundColor Green
} finally {
    if (-not $process.HasExited) {
        if (-not $shutdownSent) {
            try {
                $process.StandardInput.WriteLine("shutdown")
                $process.StandardInput.Flush()
            } catch {
            }
        }
        if (-not $process.WaitForExit(5000)) {
            $process.Kill($true)
            $process.WaitForExit()
        }
    }
    $process.Dispose()
}
