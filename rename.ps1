$ErrorActionPreference = "Stop"

# 1. Replace text in files
$files = Get-ChildItem -Path app/src -Include *.kt,*.xml,*.pro -Recurse
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    if ($content -match 'com\.example') {
        $content = $content -replace 'com\.example', 'com.potato.player'
        [IO.File]::WriteAllText($file.FullName, $content)
    }
}

# 2. Move directories
foreach ($dir in @("main", "test", "androidTest")) {
    $src = "app\src\$dir\java\com\example"
    $dest = "app\src\$dir\java\com\potato\player"
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        Move-Item -Path "$src\*" -Destination $dest -Force
        Remove-Item -Path $src -Force
    }
}
