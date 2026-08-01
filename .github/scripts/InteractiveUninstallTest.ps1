Set-StrictMode -Version Latest

if (-not ('AirPlayInstallerUiNative' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

public static class AirPlayInstallerUiNative {
    private const uint BM_CLICK = 0x00F5;
    private const uint BM_GETCHECK = 0x00F0;

    private delegate bool EnumWindowsProc(IntPtr window, IntPtr parameter);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, IntPtr parameter);

    [DllImport("user32.dll")]
    private static extern bool EnumChildWindows(
        IntPtr parent,
        EnumWindowsProc callback,
        IntPtr parameter);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(
        IntPtr window,
        StringBuilder text,
        int capacity);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetClassName(
        IntPtr window,
        StringBuilder className,
        int capacity);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr SendMessage(
        IntPtr window,
        uint message,
        IntPtr wParam,
        IntPtr lParam);

    public static IntPtr[] TopLevelWindows() {
        var windows = new List<IntPtr>();
        EnumWindows((window, parameter) => {
            if (IsWindowVisible(window)) {
                windows.Add(window);
            }
            return true;
        }, IntPtr.Zero);
        return windows.ToArray();
    }

    public static IntPtr[] DescendantWindows(IntPtr parent) {
        var windows = new List<IntPtr>();
        EnumChildWindows(parent, (window, parameter) => {
            windows.Add(window);
            return true;
        }, IntPtr.Zero);
        return windows.ToArray();
    }

    public static string Text(IntPtr window) {
        var text = new StringBuilder(1024);
        GetWindowText(window, text, text.Capacity);
        return text.ToString();
    }

    public static string ClassName(IntPtr window) {
        var className = new StringBuilder(256);
        GetClassName(window, className, className.Capacity);
        return className.ToString();
    }

    public static void Click(IntPtr window) {
        SendMessage(window, BM_CLICK, IntPtr.Zero, IntPtr.Zero);
    }

    public static int CheckState(IntPtr window) {
        return SendMessage(window, BM_GETCHECK, IntPtr.Zero, IntPtr.Zero).ToInt32();
    }
}
'@
}

function Get-AirPlayInstallerWindows {
  @([AirPlayInstallerUiNative]::TopLevelWindows() | Where-Object {
      [AirPlayInstallerUiNative]::Text($_) -like '*AirPlay Receiver*'
    })
}

function Get-AirPlayWindowControls([IntPtr] $Window) {
  @([AirPlayInstallerUiNative]::DescendantWindows($Window) | ForEach-Object {
      [PSCustomObject]@{
        Handle = $_
        Class = [AirPlayInstallerUiNative]::ClassName($_)
        Text = [AirPlayInstallerUiNative]::Text($_)
      }
    })
}

function Format-AirPlayInstallerSnapshot {
  $lines = foreach ($window in Get-AirPlayInstallerWindows) {
    "Window: $([AirPlayInstallerUiNative]::Text($window))"
    Get-AirPlayWindowControls $window | ForEach-Object {
      "  [$($_.Class)] $($_.Text)"
    }
  }
  $lines -join [Environment]::NewLine
}

function Wait-AirPlayInstallerPage(
  [string[]] $RequiredText,
  [int] $TimeoutSeconds = 30
) {
  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    foreach ($window in Get-AirPlayInstallerWindows) {
      $controls = Get-AirPlayWindowControls $window
      $allText = @([AirPlayInstallerUiNative]::Text($window)) + @($controls.Text)
      $matchesAll = $true
      foreach ($required in $RequiredText) {
        if (-not ($allText | Where-Object { $_ -like "*$required*" })) {
          $matchesAll = $false
          break
        }
      }
      if ($matchesAll) {
        return [PSCustomObject]@{ Window = $window; Controls = $controls }
      }
    }
    Start-Sleep -Milliseconds 200
  } while ([DateTime]::UtcNow -lt $deadline)

  $snapshot = Format-AirPlayInstallerSnapshot
  throw "Timed out waiting for installer page containing '$($RequiredText -join ', ')'.`n$snapshot"
}

function Find-AirPlayInstallerButton($Page, [string] $Text) {
  $button = $Page.Controls |
    Where-Object { $_.Class -eq 'Button' -and $_.Text -like "*$Text*" } |
    Select-Object -First 1
  if (!$button) {
    throw "Installer button '$Text' was not found.`n$(Format-AirPlayInstallerSnapshot)"
  }
  $button
}

function Click-AirPlayInstallerButton($Page, [string] $Text) {
  $button = Find-AirPlayInstallerButton $Page $Text
  [AirPlayInstallerUiNative]::Click($button.Handle)
}

function Get-AirPlayMsiRows(
  $Database,
  [string] $Query,
  [int] $FieldCount
) {
  $view = $Database.GetType().InvokeMember(
    'OpenView', 'InvokeMethod', $null, $Database, @($Query))
  try {
    $view.GetType().InvokeMember(
      'Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    $rows = @()
    while ($record = $view.GetType().InvokeMember(
        'Fetch', 'InvokeMethod', $null, $view, $null)) {
      $values = @()
      for ($field = 1; $field -le $FieldCount; $field++) {
        $values += $record.GetType().InvokeMember(
          'StringData', 'GetProperty', $null, $record, @($field))
      }
      $rows += [PSCustomObject]@{ Values = $values }
    }
    $rows
  } finally {
    $view.GetType().InvokeMember(
      'Close', 'InvokeMethod', $null, $view, $null) | Out-Null
  }
}

function Assert-AirPlayMsiRow(
  $Database,
  [string] $Table,
  [string[]] $Columns,
  [hashtable] $Expected
) {
  $where = @($Expected.Keys | ForEach-Object {
      $escaped = ([string] $Expected[$_]).Replace("'", "''")
      "``$_``='$escaped'"
    }) -join ' AND '
  $columnList = @($Columns | ForEach-Object { "``$_``" }) -join ','
  $rows = @(Get-AirPlayMsiRows `
      $Database `
      "SELECT $columnList FROM ``$Table`` WHERE $where" `
      $Columns.Count)
  if ($rows.Count -ne 1) {
    throw "Expected one $Table row matching $($Expected | Out-String), found $($rows.Count)"
  }
}

function Assert-AirPlayControlPanelUninstallDefinition(
  [Parameter(Mandatory = $true)][string] $UninstallString,
  [Parameter(Mandatory = $true)][string] $MsiPath
) {
  if ($UninstallString -notmatch '(?i)^\s*"?MsiExec\.exe"?\s+/I(\{[0-9A-F-]+\})\s*$') {
    throw "Control Panel does not register the MSI maintenance command: $UninstallString"
  }
  $registeredProductCode = $Matches[1].ToUpperInvariant()

  $installer = New-Object -ComObject WindowsInstaller.Installer
  $database = $installer.GetType().InvokeMember(
    'OpenDatabase', 'InvokeMethod', $null, $installer, @($MsiPath, 0))
  $productRows = @(Get-AirPlayMsiRows $database `
      "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='ProductCode'" 1)
  if ($productRows.Count -ne 1 -or
      ([string] $productRows[0].Values[0]).ToUpperInvariant() -ne $registeredProductCode) {
    throw 'The registered Control Panel ProductCode does not match the final MSI'
  }

  Assert-AirPlayMsiRow $database 'Property' @('Property', 'Value') @{
    Property = 'ARPSYSTEMCOMPONENT'; Value = '1'
  }
  Assert-AirPlayMsiRow $database 'Registry' @('Name', 'Value', 'Component_') @{
    Name = 'UninstallString'
    Value = "MsiExec.exe /I$($registeredProductCode.ToLowerInvariant())"
    Component_ = 'AirPlayControlPanelEntry'
  }
  Assert-AirPlayMsiRow $database 'Registry' @('Name', 'Value', 'Component_') @{
    Name = 'QuietUninstallString'
    Value = "MsiExec.exe /X$($registeredProductCode.ToLowerInvariant()) /qn REBOOT=ReallySuppress"
    Component_ = 'AirPlayControlPanelEntry'
  }
  Assert-AirPlayMsiRow $database 'ControlEvent' `
    @('Dialog_', 'Control_', 'Event', 'Argument', 'Condition') @{
      Dialog_ = 'MaintenanceWelcomeDlg'; Control_ = 'Next'
      Event = 'NewDialog'; Argument = 'AirPlayMaintenanceTypeDlg'; Condition = '1'
    }
  Assert-AirPlayMsiRow $database 'ControlEvent' `
    @('Dialog_', 'Control_', 'Event', 'Argument', 'Condition') @{
      Dialog_ = 'AirPlayMaintenanceTypeDlg'; Control_ = 'RemoveButton'
      Event = 'NewDialog'; Argument = 'AirPlayUninstallOptionsDlg'; Condition = '1'
    }
  Assert-AirPlayMsiRow $database 'Control' `
    @('Dialog_', 'Control', 'Type', 'Property', 'Text') @{
      Dialog_ = 'AirPlayUninstallOptionsDlg'; Control = 'RemoveUserData'
      Type = 'CheckBox'; Property = 'AIRPLAY_REMOVE_USER_DATA'
      Text = '彻底移除所有个人数据'
    }
  Assert-AirPlayMsiRow $database 'Control' @('Dialog_', 'Control', 'Text') @{
    Dialog_ = 'AirPlayExitDialog'; Control = 'UninstallTitle'
    Text = '{\WixUI_Font_Bigger}AirPlay Receiver 已卸载'
  }
  Assert-AirPlayMsiRow $database 'ControlEvent' `
    @('Dialog_', 'Control_', 'Event', 'Argument', 'Condition') @{
      Dialog_ = 'VerifyReadyDlg'; Control_ = 'Remove'
      Event = 'Remove'; Argument = 'All'; Condition = 'OutOfDiskSpace <> 1'
    }

  $defaultRows = @(Get-AirPlayMsiRows $database `
      "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='AIRPLAY_REMOVE_USER_DATA'" 1)
  if ($defaultRows.Count -ne 0) {
    throw 'The remove-personal-data checkbox is not defaulting to unchecked'
  }
}

function Invoke-AirPlayInteractiveUninstall(
  [Parameter(Mandatory = $true)][string] $UninstallString,
  [Parameter(Mandatory = $true)][string] $LogPath
) {
  if ($UninstallString -notmatch '(?i)^\s*"?MsiExec\.exe"?\s+(.+)$') {
    throw "Unsupported Control Panel uninstall command: $UninstallString"
  }

  $arguments = "$($Matches[1]) REBOOT=ReallySuppress MSIRESTARTMANAGERCONTROL=Disable /l*vx! `"$LogPath`""
  $process = Start-Process -FilePath "$env:SystemRoot\System32\msiexec.exe" `
    -ArgumentList $arguments -PassThru

  try {
    $welcome = Wait-AirPlayInstallerPage @('欢迎使用', '下一步')
    Click-AirPlayInstallerButton $welcome '下一步'

    $maintenance = Wait-AirPlayInstallerPage @('修复或卸载', '修复', '卸载')
    Click-AirPlayInstallerButton $maintenance '卸载'

    $options = Wait-AirPlayInstallerPage @(
      '彻底移除所有个人数据',
      '如果您打算以后重新安装并继续使用，请不要勾选此项。'
    )
    $checkbox = Find-AirPlayInstallerButton $options '彻底移除所有个人数据'
    if ([AirPlayInstallerUiNative]::CheckState($checkbox.Handle) -ne 0) {
      throw 'The remove-personal-data checkbox was selected by default'
    }
    Click-AirPlayInstallerButton $options '下一步'

    $nextPage = $null
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
      foreach ($required in @('结束程序并继续', '删除')) {
        try {
          $nextPage = Wait-AirPlayInstallerPage @($required) 1
          break
        } catch {
          # The running-process prompt is conditional; keep checking both paths.
        }
      }
      if ($nextPage) { break }
    } while ([DateTime]::UtcNow -lt $deadline)
    if (!$nextPage) {
      throw "Neither the running-process prompt nor remove confirmation appeared.`n$(Format-AirPlayInstallerSnapshot)"
    }

    if ($nextPage.Controls.Text | Where-Object { $_ -like '*结束程序并继续*' }) {
      Click-AirPlayInstallerButton $nextPage '结束程序并继续'
      $nextPage = Wait-AirPlayInstallerPage @('删除')
    }
    Click-AirPlayInstallerButton $nextPage '删除'

    $complete = Wait-AirPlayInstallerPage @('AirPlay Receiver 已卸载', '完成') 90
    if ($complete.Controls.Text | Where-Object { $_ -like '*个人数据*' }) {
      throw 'The completion page unexpectedly described personal-data cleanup'
    }
    Click-AirPlayInstallerButton $complete '完成'

    if (!$process.WaitForExit(30000)) {
      throw 'The interactive uninstall process did not exit after Finish'
    }
    if ($process.ExitCode -ne 0) {
      throw "Interactive uninstall failed with exit code $($process.ExitCode)"
    }
  } catch {
    $failure = $_ | Out-String
    $snapshot = Format-AirPlayInstallerSnapshot
    $installerProcesses = Get-CimInstance Win32_Process -Filter "Name='msiexec.exe'" `
      -ErrorAction SilentlyContinue |
      Select-Object ProcessId, SessionId, CommandLine |
      Format-Table -AutoSize |
      Out-String
    $diagnostic = @(
      $failure.Trim()
      "Started process: pid=$($process.Id), exited=$($process.HasExited)"
      'Visible installer windows:'
      $(if ($snapshot) { $snapshot } else { '(none)' })
      'Windows Installer processes:'
      $(if ($installerProcesses.Trim()) { $installerProcesses.Trim() } else { '(none)' })
    ) -join [Environment]::NewLine
    $annotation = $diagnostic `
      -replace '%', '%25' `
      -replace "`r", '%0D' `
      -replace "`n", '%0A'
    Write-Host "::error title=Interactive uninstall failed::$annotation"
    if (!$process.HasExited) {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
    throw
  }
}
