# 构建 + 测试 + 校验迁移，一条命令跑完
#   powershell -ExecutionPolicy Bypass -File scripts/build-and-test.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/build-and-test.ps1 -Release
param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $root
try {
    $gradle = Join-Path $root "gradlew.bat"
    if (-not (Test-Path $gradle)) { $gradle = "gradle" }

    Write-Host "== 单元测试 ==" -ForegroundColor Cyan
    & $gradle --console=plain :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }

    Write-Host "== 迁移校验 ==" -ForegroundColor Cyan
    $python = if ($env:PYTHON) { $env:PYTHON } else { "python" }
    & $python "scripts/verify_migrations.py"
    if ($LASTEXITCODE -ne 0) { throw "migration check failed" }

    Write-Host "== 打 debug 包 ==" -ForegroundColor Cyan
    & $gradle --console=plain :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }

    if ($Release) {
        Write-Host "== 打 release 包（R8）==" -ForegroundColor Cyan
        & $gradle --console=plain :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed" }
    }

    Write-Host "完成。产物在 app/build/outputs/apk/" -ForegroundColor Green
} finally {
    Pop-Location
}