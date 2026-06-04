# Script to rename ScreenModel -> ViewModel across the codebase
# This handles file renaming, class name replacement, and reference updates

$featurePath = "feature"
$appPath = "app"

# Step 1: Find all .kt files containing "ScreenModel" in content
Write-Host "=== Step 1: Finding all files with ScreenModel references ===" -ForegroundColor Cyan

$allKtFiles = Get-ChildItem -Path $featurePath, $appPath -Recurse -Filter "*.kt" -ErrorAction SilentlyContinue
$filesWithRefs = @()

foreach ($file in $allKtFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if ($content -and $content -match "ScreenModel") {
        $filesWithRefs += $file.FullName
    }
}

Write-Host "Found $($filesWithRefs.Count) files with ScreenModel references" -ForegroundColor Green

# Step 2: Rename files first (before content changes)
Write-Host "`n=== Step 2: Renaming files ===" -ForegroundColor Cyan

$modelFiles = Get-ChildItem -Path $featurePath, $appPath -Recurse -Filter "*ScreenModel*.kt" -ErrorAction SilentlyContinue

foreach ($file in $modelFiles) {
    $oldName = $file.Name
    $newName = $oldName -replace 'ScreenModel', 'ViewModel'
    $newPath = Join-Path $file.DirectoryName $newName
    
    if (-not (Test-Path $newPath)) {
        Rename-Item -Path $file.FullName -NewName $newName
        Write-Host "  Renamed: $oldName -> $newName" -ForegroundColor Yellow
    } else {
        Write-Host "  SKIP (target exists): $newName" -ForegroundColor DarkYellow
    }
}

# Step 3: Replace content in all affected files
Write-Host "`n=== Step 3: Replacing ScreenModel -> ViewModel in file contents ===" -ForegroundColor Cyan

# Re-scan after renames
$allKtFiles = Get-ChildItem -Path $featurePath, $appPath -Recurse -Filter "*.kt" -ErrorAction SilentlyContinue

$replacements = @(
    @{ Pattern = "ScreenModel"; Replacement = "ViewModel" },
    @{ Pattern = "screenModel"; Replacement = "viewModel" }
)

$count = 0
foreach ($file in $allKtFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content -or -not ($content -match "ScreenModel|screenModel")) {
        continue
    }
    
    $newContent = $content
    
    # Replace class/type references (ScreenModel -> ViewModel)
    # Be careful not to replace in string literals or comments that reference external things
    $newContent = $newContent -replace 'ScreenModel', 'ViewModel'
    
    # Replace variable names (screenModel -> viewModel)
    $newContent = $newContent -replace 'screenModel', 'viewModel'
    
    if ($newContent -ne $content) {
        Set-Content -Path $file.FullName -Value $newContent -NoNewline
        $count++
        Write-Host "  Updated: $($file.Name)" -ForegroundColor DarkGreen
    }
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "Files renamed: $($modelFiles.Count)" -ForegroundColor Green
Write-Host "Files content updated: $count" -ForegroundColor Green
Write-Host "Done!" -ForegroundColor Green