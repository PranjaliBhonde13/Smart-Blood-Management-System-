<#
.SYNOPSIS
  Creates and migrates the Smart Blood Emergency Network database in one go.

.DESCRIPTION
  1. Runs database/migrations/000_create_database.sql as an admin account:
     creates the database and the app user.
  2. Applies every pending Flyway migration from
     backend/src/main/resources/db/migration (V1__..., V2__..., ...) in version order.
  3. Records each applied migration in flyway_schema_history with Flyway's own
     checksum, so the backend sees the schema as already migrated on startup and
     only runs migrations added later.

  Safe to re-run: already-applied migrations are skipped.

.EXAMPLE
  .\database\setup-database.ps1
  .\database\setup-database.ps1 -Port 3309 -AdminPassword secret
  .\database\setup-database.ps1 -Reset      # drop and rebuild the database
#>
[CmdletBinding()]
param(
    [string]$DbHost        = '127.0.0.1',
    [int]   $Port          = 3306,
    [string]$DbName        = 'SBN',
    [string]$AdminUser     = 'root',
    [string]$AdminPassword = '',
    [string]$AppUser       = 'sben',
    [string]$AppPassword   = 'sben',
    [string]$MysqlExe      = '',
    [switch]$Reset,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root          = Split-Path -Parent $PSScriptRoot
$bootstrapSql  = Join-Path $PSScriptRoot 'migrations\000_create_database.sql'
$flywayDir     = Join-Path $root 'backend\src\main\resources\db\migration'

# ---------- locate the mysql client ----------
function Find-MysqlClient {
    if ($MysqlExe) { return $MysqlExe }
    $onPath = Get-Command mysql -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    foreach ($candidate in 'C:\xampp\mysql\bin\mysql.exe',
                           'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
                           'C:\Program Files\MariaDB 11.4\bin\mysql.exe') {
        if (Test-Path $candidate) { return $candidate }
    }
    throw 'mysql client not found. Install MySQL/MariaDB (or XAMPP) or pass -MysqlExe <path>.'
}
$mysql = Find-MysqlClient

# Runs SQL (string) against the server. Password goes via MYSQL_PWD, never the command line.
function Invoke-Sql {
    param([string]$Sql, [string]$User, [string]$Password, [string]$Database = '', [switch]$Raw)
    $tmp = [IO.Path]::GetTempFileName()
    [IO.File]::WriteAllText($tmp, $Sql, (New-Object Text.UTF8Encoding($false)))
    $cliArgs = @("--host=$DbHost", "--port=$Port", "--user=$User", '--default-character-set=utf8mb4', '--batch')
    if ($Raw) { $cliArgs += '--skip-column-names' }
    if ($Database) { $cliArgs += "--database=$Database" }
    $env:MYSQL_PWD = $Password
    try {
        $out = cmd /c "`"$mysql`" $($cliArgs -join ' ') < `"$tmp`" 2>&1"
        if ($LASTEXITCODE -ne 0) { throw ($out | Out-String).Trim() }
        return $out
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

# ---------- Flyway checksum (CRC32 over each line, line breaks excluded, BOM stripped) ----------
Add-Type -TypeDefinition @'
public static class FlywayChecksum {
    static readonly uint[] Table = Build();
    static uint[] Build() {
        var t = new uint[256];
        for (uint i = 0; i < 256; i++) {
            uint c = i;
            for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            t[i] = c;
        }
        return t;
    }
    public static int Compute(string path) {
        uint crc = 0xFFFFFFFFu;
        bool first = true;
        using (var r = new System.IO.StreamReader(path, new System.Text.UTF8Encoding(false), false)) {
            string line;
            while ((line = r.ReadLine()) != null) {
                if (first) { if (line.Length > 0 && line[0] == '\uFEFF') line = line.Substring(1); first = false; }
                foreach (byte b in System.Text.Encoding.UTF8.GetBytes(line)) crc = Table[(crc ^ b) & 0xFF] ^ (crc >> 8);
            }
        }
        return unchecked((int)(crc ^ 0xFFFFFFFFu));
    }
}
'@

Write-Host "mysql client : $mysql"
Write-Host "server       : ${DbHost}:$Port"
Write-Host "database     : $DbName (app user '$AppUser')"
Write-Host ''

# ---------- 0. connectivity ----------
try { Invoke-Sql -Sql 'SELECT 1;' -User $AdminUser -Password $AdminPassword | Out-Null }
catch { throw "Cannot connect as '$AdminUser' to ${DbHost}:$Port. Is the server running (XAMPP: start MySQL)? `n$_" }

# ---------- optional reset ----------
if ($Reset) {
    if (-not $Force) {
        $answer = Read-Host "This DROPS database '$DbName' and all its data. Type the database name to confirm"
        if ($answer -ne $DbName) { Write-Host 'Aborted.'; exit 1 }
    }
    Invoke-Sql -Sql "DROP DATABASE IF EXISTS ``$DbName``;" -User $AdminUser -Password $AdminPassword | Out-Null
    Write-Host "[reset] dropped $DbName"
}

# ---------- 1. bootstrap: database + app user ----------
$bootstrap = (Get-Content $bootstrapSql -Raw) `
    -replace '\{\{DB_NAME\}\}', $DbName `
    -replace '\{\{APP_USER\}\}', $AppUser `
    -replace '\{\{APP_PASSWORD\}\}', ($AppPassword -replace "'", "''")
Invoke-Sql -Sql $bootstrap -User $AdminUser -Password $AdminPassword | Out-Null
Write-Host "[ok] 000_create_database.sql  (database + user)"

# ---------- 2. Flyway history table (same DDL Flyway uses for MySQL/MariaDB) ----------
Invoke-Sql -Database $DbName -User $AdminUser -Password $AdminPassword -Sql @'
CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
  `installed_rank` INT NOT NULL,
  `version` VARCHAR(50),
  `description` VARCHAR(200) NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `script` VARCHAR(1000) NOT NULL,
  `checksum` INT,
  `installed_by` VARCHAR(100) NOT NULL,
  `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` INT NOT NULL,
  `success` BOOL NOT NULL,
  CONSTRAINT `flyway_schema_history_pk` PRIMARY KEY (`installed_rank`),
  INDEX `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB;
'@ | Out-Null

$failed = Invoke-Sql -Raw -Database $DbName -User $AdminUser -Password $AdminPassword `
    -Sql 'SELECT script FROM flyway_schema_history WHERE success = 0;'
if ($failed) { throw "flyway_schema_history has failed migrations ($($failed -join ', ')). Fix them or run with -Reset." }

$applied = @{}
Invoke-Sql -Raw -Database $DbName -User $AdminUser -Password $AdminPassword `
    -Sql 'SELECT version, checksum FROM flyway_schema_history WHERE version IS NOT NULL;' |
    ForEach-Object { $cols = $_ -split "`t"; $applied[$cols[0]] = $cols[1] }

# ---------- 3. apply pending versioned migrations in order ----------
$migrations = Get-ChildItem $flywayDir -Filter 'V*__*.sql' | ForEach-Object {
    if ($_.Name -notmatch '^V(\d+(?:[._]\d+)*)__(.+)\.sql$') { return }
    $version = $Matches[1] -replace '_', '.'
    [pscustomobject]@{
        File        = $_
        Version     = $version
        SortKey     = [version](($version.Split('.') + @('0','0','0'))[0..3] -join '.')
        Description = $Matches[2] -replace '_', ' '
    }
} | Sort-Object SortKey

foreach ($m in $migrations) {
    $checksum = [FlywayChecksum]::Compute($m.File.FullName)
    if ($applied.ContainsKey($m.Version)) {
        if ($applied[$m.Version] -ne "$checksum") {
            Write-Warning "$($m.File.Name) was edited after it was applied (checksum differs). Never edit an applied migration - add a new V file."
        }
        Write-Host "[skip] $($m.File.Name)  (already applied)"
        continue
    }

    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        Invoke-Sql -Database $DbName -User $AdminUser -Password $AdminPassword -Sql (Get-Content $m.File.FullName -Raw) | Out-Null
    } catch {
        throw "Migration $($m.File.Name) failed:`n$_`nThe database may be partially migrated; fix the script and re-run with -Reset."
    }
    $sw.Stop()

    $desc = $m.Description -replace "'", "''"
    Invoke-Sql -Database $DbName -User $AdminUser -Password $AdminPassword -Sql @"
INSERT INTO flyway_schema_history
  (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
SELECT COALESCE(MAX(installed_rank), 0) + 1, '$($m.Version)', '$desc', 'SQL', '$($m.File.Name)',
       $checksum, '$AppUser', $($sw.ElapsedMilliseconds), 1
FROM flyway_schema_history;
"@ | Out-Null
    Write-Host "[ok] $($m.File.Name)  ($($sw.ElapsedMilliseconds) ms)"
}

# ---------- 4. summary ----------
Write-Host ''
Invoke-Sql -Database $DbName -User $AppUser -Password $AppPassword `
    -Sql 'SELECT role, email FROM users WHERE is_deleted = 0 ORDER BY id;' | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host "Done. Backend settings (backend/.env):"
Write-Host "  DB_HOST=localhost  DB_PORT=$Port  DB_NAME=$DbName  DB_USERNAME=$AppUser  DB_PASSWORD=<app password>"
