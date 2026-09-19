# ==============================================================================
# Script: package_exe.ps1
# Description: Automated end-to-end pipeline to compile Prism Zoho Mail Backup Tool Tool,
#              generate standalone portable EXE (via jpackage), and create
#              a single-file Windows Setup installer (via Advanced Installer).
# ==============================================================================

param (
    [switch]$SkipBuild,        # Pass -SkipBuild to skip Maven rebuild and use existing JAR
    [switch]$AppImageOnly      # Pass -AppImageOnly to stop after creating Prism-Zoho-Mail-Backup-Tool.exe
)

$ErrorActionPreference = "Stop"

# --- 1. CONFIGURATION & PREREQUISITES ---
$ProjectDir = $PSScriptRoot
if (-not $ProjectDir) { $ProjectDir = Get-Location }

# JDK 21 setup (Oracle JDK 21 or fallback)
$JdkCandidates = @(
    "D:\IDE and Zip Softwares\Java\jdk-21_windows-x64_bin\jdk-21.0.8",
    "C:\Users\akash\.jdks\ms-17.0.18",
    "D:\IDE and Zip Softwares\Java\jdk-24_windows-x64_bin\jdk-24.0.2"
)
foreach ($cand in $JdkCandidates) {
    if (Test-Path $cand) {
        $env:JAVA_HOME = $cand
        break
    }
}
if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME is not set. Please set JAVA_HOME."
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$AiPath = "C:\Program Files (x86)\Caphyon\Advanced Installer 23.9\bin\x86\AdvancedInstaller.com"
$TargetDir = Join-Path $ProjectDir "target"
$PackageInputDir = Join-Path $TargetDir "package-input"
$AppImageDir = Join-Path $TargetDir "app-image"
$AppFolder = Join-Path $AppImageDir "Prism-Zoho-Mail-Backup-Tool"
$OutputDir = "I:\My Drive\Akas\EXE"
$AipFile = Join-Path $ProjectDir "installer.aip"
$LogoIcon = Join-Path $ProjectDir "installer-assets\logo.ico"
$LibsDir = Join-Path $ProjectDir "libs"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  Prism Zoho Mail Backup Tool Tool - Full EXE & Installer Pipeline" -ForegroundColor Cyan
Write-Host "  JAVA_HOME : $env:JAVA_HOME" -ForegroundColor Gray
Write-Host "  Project   : $ProjectDir" -ForegroundColor Gray
Write-Host "========================================================`n" -ForegroundColor Cyan

# --- 2. MAVEN BUILD STAGE ---
if (-not $SkipBuild) {
    Write-Host "[STEP 1/3] Building Windows Distribution JAR with Maven..." -ForegroundColor Yellow
    Set-Location $ProjectDir
    mvn clean package "-DskipTests"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven build failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host "[STEP 1/3] Skipping Maven rebuild (-SkipBuild flag active)..." -ForegroundColor Gray
}

# --- 3. STANDALONE APP-IMAGE & EXE STAGE (jpackage) ---
Write-Host "`n[STEP 2/3] Generating Standalone Application Directory & EXE via jpackage..." -ForegroundColor Yellow

# Find latest protected jar
$ProtectedJar = Get-ChildItem -Path $TargetDir -Filter "imap-backup-tool-*-protected.jar" | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -First 1

if (-not $ProtectedJar) {
    Write-Error "No protected JAR found in $TargetDir! Did the Maven build produce an imap-backup-tool-*-protected.jar?"
}

Write-Host "Found protected JAR: $($ProtectedJar.Name) ($([math]::Round($ProtectedJar.Length / 1MB, 2)) MB)" -ForegroundColor Gray

# Clean and prepare package-input directory
if (Test-Path $PackageInputDir) {
    Remove-Item $PackageInputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $PackageInputDir -Force | Out-Null

Copy-Item $ProtectedJar.FullName (Join-Path $PackageInputDir "imap-backup-tool-1.0-SNAPSHOT.jar") -Force
# Note: aspose-email and chilkat are already shaded inside the protected fat JAR,
# so we do not copy duplicate JARs into package-input (saves ~13 MB in installer).
if (Test-Path (Join-Path $ProjectDir "installer-assets\splash.png")) {
    Copy-Item (Join-Path $ProjectDir "installer-assets\splash.png") (Join-Path $PackageInputDir "splash.png") -Force
}

# Clean previous app-image output
if (Test-Path $AppImageDir) {
    Write-Host "Cleaning previous app-image directory..." -ForegroundColor Gray
    Remove-Item $AppImageDir -Recurse -Force
}

# Run jpackage
$JpackageExe = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
Write-Host "Running jpackage ($JpackageExe)..." -ForegroundColor Cyan

$JpackageArgs = @(
    "--type", "app-image",
    "--name", "Prism-Zoho-Mail-Backup-Tool",
    "--input", $PackageInputDir,
    "--main-jar", "imap-backup-tool-1.0-SNAPSHOT.jar",
    "--main-class", "com.pstconverter.Launcher",
    "--icon", $LogoIcon,
    "--dest", $AppImageDir,
    "--java-options", "-Xmx4g",
    "--java-options", "-XX:+UseG1GC",
    "--java-options", "-Djava.library.path=app",
    "--java-options", "-splash:app/splash.png",
    "--add-modules", "java.base,java.desktop,java.sql,java.net.http,java.logging,java.xml,java.naming,java.management,jdk.unsupported"
)

& $JpackageExe @JpackageArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed with exit code $LASTEXITCODE"
}

$ExePath = Join-Path $AppFolder "Prism-Zoho-Mail-Backup-Tool.exe"
if (-not (Test-Path $ExePath)) {
    Write-Error "Prism-Zoho-Mail-Backup-Tool.exe was not created at expected location: $ExePath"
}

Write-Host "[SUCCESS] Standalone Application EXE generated:" -ForegroundColor Green
Write-Host "  -> $ExePath`n" -ForegroundColor White

if ($AppImageOnly) {
    Write-Host "Done! (-AppImageOnly flag active, skipping installer step)" -ForegroundColor Green
    exit 0
}

# --- 4. ADVANCED INSTALLER SETUP STAGE ---
Write-Host "[STEP 3/3] Building Single-File Setup Installer with Advanced Installer..." -ForegroundColor Yellow

if (-not (Test-Path $AiPath)) {
    Write-Host "Advanced Installer not found at: $AiPath. Skipping installer build." -ForegroundColor Yellow
    exit 0
}

# Clean old aip
if (Test-Path $AipFile) {
    Remove-Item $AipFile -Force
}

# Auto-sync fresh prism_left_banner.png to dialog.bmp
$BannerBmp = Join-Path $ProjectDir "installer-assets\banner.bmp"
$DialogBmp = Join-Path $ProjectDir "installer-assets\dialog.bmp"

$UserBannerPath = "D:\Akas\Git Synched\Obsidian\Obsidian-Work\Prism Migration\Images and Icons\prism_left_banner.png"
if (Test-Path $UserBannerPath) {
    Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue
    $leftSrc = [System.Drawing.Image]::FromFile($UserBannerPath)
    $dialogBmpObj = New-Object System.Drawing.Bitmap(500, 316, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $gD = [System.Drawing.Graphics]::FromImage($dialogBmpObj)
    $gD.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gD.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $gD.FillRectangle($whiteBrush, 0, 0, 500, 316)
    $gD.DrawImage($leftSrc, 0, 0, 164, 316)
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(226, 232, 240), 1)
    $gD.DrawLine($pen, 164, 0, 164, 316)
    $leftSrc.Dispose(); $gD.Dispose(); $whiteBrush.Dispose(); $pen.Dispose()
    $dialogBmpObj.Save($DialogBmp, [System.Drawing.Imaging.ImageFormat]::Bmp)
    $dialogBmpObj.Dispose()
    Write-Host "  Auto-synced dialog.bmp with user's latest prism_left_banner.png!" -ForegroundColor Green
}

# 1. New Professional Project
Write-Host "  Creating new project..." -ForegroundColor Gray
& $AiPath /newproject $AipFile -type professional

# 2. Product Details & Metadata
Write-Host "  Setting product details..." -ForegroundColor Gray
& $AiPath /edit $AipFile /SetProperty ProductName="Prism Zoho Mail Backup Tool Tool"
& $AiPath /edit $AipFile /SetVersion "1.0.0"
& $AiPath /edit $AipFile /SetProperty Manufacturer="Prism Software"
& $AiPath /edit $AipFile /SetProperty ARPHELPLINK="https://www.prismimapbackup.com/support"
& $AiPath /edit $AipFile /SetProperty ARPURLINFOABOUT="https://www.prismimapbackup.com"
& $AiPath /edit $AipFile /SetProperty ARPURLUPDATEINFO="https://www.prismimapbackup.com"
& $AiPath /edit $AipFile /SetProperty ARPCOMMENTS="Backup and Export IMAP Emails Safely to 17 Formats"

# 3. Add Application Files
Write-Host "  Adding app files to APPDIR..." -ForegroundColor Gray
& $AiPath /edit $AipFile /AddFolder APPDIR "$AppFolder"

# 4. Control Panel Icon
Write-Host "  Setting control panel icon..." -ForegroundColor Gray
& $AiPath /edit $AipFile /SetIcon -icon "$LogoIcon"

# 5. Shortcuts
Write-Host "  Creating Desktop and Start Menu shortcuts..." -ForegroundColor Gray
& $AiPath /edit $AipFile /NewShortcut -name "Prism Zoho Mail Backup Tool Tool" -dir DesktopFolder -target "[APPDIR]Prism-Zoho-Mail-Backup-Tool\Prism-Zoho-Mail-Backup-Tool.exe" -icon "$LogoIcon"
& $AiPath /edit $AipFile /NewShortcut -name "Prism Zoho Mail Backup Tool Tool" -dir SHORTCUTDIR -target "[APPDIR]Prism-Zoho-Mail-Backup-Tool\Prism-Zoho-Mail-Backup-Tool.exe" -icon "$LogoIcon"

# 6. Custom Branding & Bitmaps
Write-Host "  Setting custom Prism branding..." -ForegroundColor Gray
& $AiPath /edit $AipFile /SetBitmap -banner "$BannerBmp" -dialog "$DialogBmp" -buildname DefaultBuild

# 7. Single EXE Output Settings
Write-Host "  Configuring Single-File Setup EXE..." -ForegroundColor Gray
& $AiPath /edit $AipFile /SetOutputType ExeInside -buildname DefaultBuild
& $AiPath /edit $AipFile /SetPackageName "Prism-Zoho-Mail-Backup-Tool-Setup.exe" -buildname DefaultBuild
& $AiPath /edit $AipFile /SetOutputExeIcon -buildname DefaultBuild -iconpath "$LogoIcon"

# 8. Output Directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
& $AiPath /edit $AipFile /SetOutputLocation -buildname DefaultBuild -path "$OutputDir"

# 9. Build
Write-Host "  Building Installer EXE..." -ForegroundColor Cyan
& $AiPath /build $AipFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "Advanced Installer build failed with exit code $LASTEXITCODE"
}

# 10. Sync to Drive
$driveJarDir = "I:\My Drive\Akas\Jars\Zoho Mail Backup Tool"
if (-not (Test-Path $driveJarDir)) { New-Item -ItemType Directory -Path $driveJarDir -Force | Out-Null }
Copy-Item $ProtectedJar.FullName (Join-Path $driveJarDir "Prism-Zoho-Mail-Backup-Tool.jar") -Force

$destAdvance = "I:\My Drive\Akas\Package Maker\Advance Package"
if (-not (Test-Path $destAdvance)) { New-Item -ItemType Directory -Path $destAdvance -Force | Out-Null }
Copy-Item $AipFile (Join-Path $destAdvance "Prism-Zoho-Mail-Backup-Tool.aip") -Force
Copy-Item (Join-Path $ProjectDir "package_exe.ps1") (Join-Path $destAdvance "package_exe_imap.ps1") -Force

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green
Write-Host "  1. Standalone Portable App: $AppFolder" -ForegroundColor White
Write-Host "     Executable: $ExePath" -ForegroundColor White
Write-Host "  2. Single-File Setup Installer: $OutputDir" -ForegroundColor White
Write-Host "     -> Prism-Zoho-Mail-Backup-Tool-Setup.exe" -ForegroundColor White
Write-Host "  3. Standalone Protected JAR: $driveJarDir" -ForegroundColor White
Write-Host "     -> Prism-Zoho-Mail-Backup-Tool.jar" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Green
