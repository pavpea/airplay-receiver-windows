Set-StrictMode -Version Latest

function Get-AirPlayInstallerClientProcesses {
    # The Windows Installer service runs as msiexec in session 0 and may remain
    # idle for several minutes after a successful installation. It is not a
    # child installer that should keep a CI step alive.
    @(Get-Process msiexec -ErrorAction SilentlyContinue |
        Where-Object { $_.SessionId -ne 0 })
}

function ConvertTo-AirPlayInstallerArgument {
    param([Parameter(Mandatory)][string] $Argument)

    # msiexec parses PROPERTY=value assignments from the raw command line
    # instead of treating them like ordinary argv entries. Quoting the whole
    # assignment ("INSTALLDIR=C:\Program Files\App") opens its usage dialog;
    # the property name must remain outside the quotes.
    if ($Argument -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $Matches[1]
        $value = $Matches[2]
        if ($value -match '\s|"') {
            return "$name=`"$($value.Replace('"', '\"'))`""
        }
        return $Argument
    }
    if ($Argument -match '\s|"') {
        return "`"$($Argument.Replace('"', '\"'))`""
    }
    $Argument
}

function Invoke-AirPlayInstallerProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [Parameter(Mandatory)]
        [string] $LogPath,

        [int] $TimeoutSeconds = 180
    )

    $beforeMsi = @(Get-AirPlayInstallerClientProcesses |
        ForEach-Object { $_.Id })
    # Build the native command line once so MSI properties retain the syntax
    # PROPERTY="value with spaces". ProcessStartInfo.ArgumentList would quote
    # the complete assignment and msiexec would display an interactive usage
    # dialog even when /qn was supplied.
    $argumentLine = ($Arguments |
        ForEach-Object { ConvertTo-AirPlayInstallerArgument $_ }) -join ' '
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.UseShellExecute = $false
    $startInfo.Arguments = $argumentLine
    $process = [System.Diagnostics.Process]::Start($startInfo)
    if ($null -eq $process) {
        throw "Failed to start installer process '$FilePath'"
    }
    "Started installer PID $($process.Id): $FilePath $argumentLine" |
        Write-Host
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    try {
        $nextHeartbeat = [DateTime]::UtcNow.AddSeconds(15)
        while (!$process.WaitForExit(1000)) {
            if ([DateTime]::UtcNow -ge $deadline) {
                throw "process $($process.Id) did not exit before the deadline"
            }
            if ([DateTime]::UtcNow -ge $nextHeartbeat) {
                $process.Refresh()
                $logBytes = if (Test-Path -LiteralPath $LogPath) {
                    (Get-Item -LiteralPath $LogPath).Length
                } else {
                    0
                }
                "Waiting for installer PID $($process.Id); " +
                    "window='$($process.MainWindowTitle)'; logBytes=$logBytes" |
                    Write-Host
                $nextHeartbeat = [DateTime]::UtcNow.AddSeconds(15)
            }
        }

        do {
            $newMsi = @(Get-AirPlayInstallerClientProcesses |
                Where-Object { $_.Id -notin $beforeMsi })
            if ($newMsi.Count -eq 0) {
                break
            }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)

        if ($newMsi.Count -ne 0) {
            throw "Windows Installer child processes did not exit"
        }
    } catch {
        $processSnapshot = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -in @('msiexec.exe', 'AirPlay Receiver.exe') -or
                $_.ProcessId -eq $process.Id -or
                $_.ParentProcessId -eq $process.Id
            } |
            Select-Object ProcessId, ParentProcessId, Name, CommandLine
        $windowSnapshot = Get-Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Id -eq $process.Id -or
                $_.Id -in @($processSnapshot.ProcessId)
            } |
            Select-Object Id, ProcessName, MainWindowHandle, MainWindowTitle,
                Responding
        $diagnostics = @(
            "Installer: $FilePath"
            "Arguments: $($Arguments -join ' ')"
            "TimeoutSeconds: $TimeoutSeconds"
            "Error: $($_.Exception.Message)"
            "Processes:"
            ($processSnapshot | Format-List | Out-String)
            "Windows:"
            ($windowSnapshot | Format-List | Out-String)
        )
        if (!(Test-Path -LiteralPath $LogPath)) {
            $diagnostics | Set-Content -LiteralPath $LogPath -Encoding utf8
        } else {
            $diagnostics | Add-Content -LiteralPath $LogPath -Encoding utf8
        }
        if (Test-Path -LiteralPath $LogPath) {
            Get-Content -LiteralPath $LogPath -Tail 200
        }
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        Get-AirPlayInstallerClientProcesses |
            Where-Object { $_.Id -notin $beforeMsi } |
            Stop-Process -Force -ErrorAction SilentlyContinue
        throw "Installer process '$FilePath' did not finish within $TimeoutSeconds seconds: $($_.Exception.Message)"
    }

    $process.Refresh()
    if ($process.ExitCode -ne 0) {
        if (Test-Path -LiteralPath $LogPath) {
            Get-Content -LiteralPath $LogPath -Tail 200
        }
        throw "Installer process '$FilePath' failed with exit code $($process.ExitCode)"
    }
}
