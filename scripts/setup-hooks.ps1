# Installs Ephyra's local git hooks so spotless/ktlint failures are caught
# before pushing (CI build.yml runs `gradlew spotlessCheck` and will fail
# otherwise). Run once per clone:
#   powershell -ExecutionPolicy Bypass -File scripts\setup-hooks.ps1

git config core.hooksPath scripts/hooks
Write-Host "Installed git hooks (core.hooksPath = scripts/hooks)." -ForegroundColor Green
Write-Host "Pre-commit will now run 'gradlew spotlessCheck' when .kt/.kts/.xml files are staged."
Write-Host "Emergency bypass: git commit --no-verify"
