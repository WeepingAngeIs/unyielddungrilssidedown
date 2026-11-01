$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$index = Invoke-RestMethod 'https://vrchat.github.io/packages/index.json'
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'Packages/vpm-manifest.json') -Raw | ConvertFrom-Json
foreach ($dependency in $manifest.dependencies.PSObject.Properties) {
    $name = $dependency.Name
    $version = $dependency.Value.version
    $target = Join-Path $projectRoot "Packages/$name"
    if (Test-Path -LiteralPath (Join-Path $target 'package.json')) { continue }
    $package = $index.packages.$name.versions.$version
    if (-not $package.url -or $package.url -notlike 'https://*') { throw "Package unavailable: $name $version. Restore through VRChat Creator Companion." }
    $archive = Join-Path ([IO.Path]::GetTempPath()) ("NeonNook-" + [guid]::NewGuid() + '.zip')
    Invoke-WebRequest -Uri $package.url -OutFile $archive
    if ($package.zipSHA256) {
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $package.zipSHA256) { throw "Package hash mismatch: $name" }
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $target
    Remove-Item -LiteralPath $archive
    Write-Host "Installed $name $version"
}
Write-Host 'Open this folder in Unity 2022.3.22f1, then Assets/IdleLounge/Scenes/NeonNook.unity.'
