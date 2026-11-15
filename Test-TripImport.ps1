# Synthetic MariaDB integration test. Does not read or modify application/source records.
# Creates two uniquely named databases; drops only those databases in finally.
param([string]$MariaDbClient = 'C:\Program Files\MariaDB 12.3\bin\mariadb.exe')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $project 'set_agent_sandbo_env.ps1')
$address = [uri]($env:DB_URL -replace '^jdbc:', '')
$dbPort = if ($address.Port -gt 0) { $address.Port } else { 3306 }
$testSuffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$source = 'meeter_trip_test_src_' + $testSuffix
$target = 'meeter_trip_test_dst_' + $testSuffix
$hadPassword = Test-Path Env:MYSQL_PWD
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:DB_PASSWORD
$sourceCreated = $false
$targetCreated = $false
function Invoke-TestSql([string]$sql, [string]$expectedError = '') {
    $result = $sql | & $MariaDbClient "--host=$($address.Host)" "--port=$dbPort" "--user=$env:DB_USER" --default-character-set=utf8mb4 --batch --skip-column-names 2>&1
    $code = $LASTEXITCODE
    $output = ($result | ForEach-Object { "$_" }) -join "`n"
    if ($expectedError) {
        if ($code -eq 0 -or !$output.Contains($expectedError)) { throw "Expected SQL failure: $expectedError. Actual: $output" }
        return
    }
    if ($code -ne 0) { throw "Test SQL failed: $output" }
    return $output
}
function Assert-TestSql([string]$query, [string]$expected, [string]$label) {
    $actual = Invoke-TestSql $query
    # Some MariaDB clients emit a passwordless-login warning on stderr.
    $actual = (($actual -split "`n" | Where-Object { $_ -notmatch '^WARNING:' }) -join "`n").Trim()
    if ($actual -ne $expected) { throw "$label failed. Expected [$expected], got [$actual]" }
    Write-Host "PASS: $label"
}
try {
    # No IF NOT EXISTS: never adopt a pre-existing schema for cleanup.
    Invoke-TestSql "CREATE DATABASE $source CHARACTER SET utf8mb4;" | Out-Null
    $sourceCreated = $true
    Invoke-TestSql "CREATE DATABASE $target CHARACTER SET utf8mb4;" | Out-Null
    $targetCreated = $true
    $fixture = @"
CREATE TABLE $source.citybreaks (ID INT PRIMARY KEY, start_place_id INT, Place TEXT NOT NULL, category MEDIUMTEXT NOT NULL, Date_start DATETIME NOT NULL, Date_stop DATETIME NOT NULL, long_description VARCHAR(6000) NOT NULL) ENGINE=InnoDB;
CREATE TABLE $source.citybreak_companion (id INT PRIMARY KEY AUTO_INCREMENT,citybreak_id INT,human_id INT) ENGINE=InnoDB;
CREATE TABLE $source.trip_place (id INT PRIMARY KEY AUTO_INCREMENT,trip_id INT,place_id INT) ENGINE=InnoDB;
CREATE TABLE $target.people (id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE $target.places (id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE $target.app_users (id BIGINT PRIMARY KEY, username VARCHAR(64),human_id BIGINT NOT NULL,FOREIGN KEY(human_id) REFERENCES $target.people(id)) ENGINE=InnoDB;
CREATE TABLE $target.interactions (id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at DATETIME(6) NOT NULL,end_date DATETIME(6),long_desc VARCHAR(10240),name VARCHAR(128) NOT NULL,start_date DATETIME(6) NOT NULL,uuid UUID NOT NULL UNIQUE,created_by_id BIGINT,place_id BIGINT,FOREIGN KEY(created_by_id) REFERENCES $target.app_users(id),FOREIGN KEY(place_id) REFERENCES $target.places(id)) ENGINE=InnoDB;
CREATE TABLE $target.trips (id BIGINT PRIMARY KEY,FOREIGN KEY(id) REFERENCES $target.interactions(id)) ENGINE=InnoDB;
CREATE TABLE $target.interaction_human (id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at DATETIME(6) NOT NULL,created_by_id BIGINT NOT NULL,human_id BIGINT NOT NULL,interaction_id BIGINT NOT NULL,UNIQUE(interaction_id,human_id),FOREIGN KEY(created_by_id) REFERENCES $target.app_users(id),FOREIGN KEY(human_id) REFERENCES $target.people(id),FOREIGN KEY(interaction_id) REFERENCES $target.interactions(id)) ENGINE=InnoDB;
CREATE TABLE $target.trip_place (id BIGINT AUTO_INCREMENT PRIMARY KEY,created_at DATETIME(6) NOT NULL,created_by_id BIGINT NOT NULL,place_id BIGINT NOT NULL,trip_id BIGINT NOT NULL,UNIQUE(trip_id,place_id),FOREIGN KEY(created_by_id) REFERENCES $target.app_users(id),FOREIGN KEY(place_id) REFERENCES $target.places(id),FOREIGN KEY(trip_id) REFERENCES $target.trips(id)) ENGINE=InnoDB;
INSERT INTO $target.people VALUES (77),(88);
INSERT INTO $target.places VALUES (10),(11);
INSERT INTO $target.app_users VALUES (100,'pszkudlarek',77);
INSERT INTO $target.interactions VALUES (1,'2020-01-01',NULL,'Existing','Existing','2020-01-01',UUID(),100,NULL);
INSERT INTO $source.citybreaks VALUES (1,10,'Synthetic trip','trip','2021-03-04 13:14:15','2021-03-06 16:17:18','Synthetic description'),(2,NULL,'','other','2022-02-01','2022-02-02','');
INSERT INTO $source.citybreak_companion(citybreak_id,human_id) VALUES (1,88),(1,88),(1,77);
INSERT INTO $source.trip_place(trip_id,place_id) VALUES (1,10),(1,11),(1,11);
"@
    Invoke-TestSql $fixture | Out-Null
    $migration = [IO.File]::ReadAllText((Join-Path $project 'migrate_trips.sql')).Replace('real_meeter', $source).Replace('vibecoded_meeter', $target)
    Invoke-TestSql $migration | Out-Null
    Assert-TestSql "SELECT CONCAT((SELECT COUNT(*) FROM $target.trips),',',(SELECT COUNT(*) FROM $target.interaction_human),',',(SELECT COUNT(*) FROM $target.trip_place),',',(SELECT COUNT(*) FROM $target.interactions));" '2,3,2,3' 'first import, deduplication and existing-record preservation'
    Assert-TestSql "SELECT CONCAT(DATE_FORMAT(start_date,'%Y-%m-%d %H:%i:%s'),',',DATE_FORMAT(end_date,'%Y-%m-%d %H:%i:%s'),',',place_id) FROM $target.interactions WHERE name='Synthetic trip';" '2021-03-04 13:14:15,2021-03-06 16:17:18,10' 'local dates and start place preserved'
    Assert-TestSql "SELECT COUNT(*) FROM $target.interactions WHERE name='Podróż #2';" '1' 'empty title fallback'
    Assert-TestSql "SELECT COUNT(*) FROM $target.migration_real_meeter_trips WHERE source_trip_id=2 AND source_category='other' AND source_title='';" '1' 'unmapped source metadata preserved'
    Assert-TestSql "SELECT COUNT(*) FROM $target.interaction_human h JOIN $target.interactions i ON i.id=h.interaction_id WHERE h.created_by_id=100 AND h.created_at=i.created_at;" '3' 'server-managed relationship metadata'
    $snapshotQuery = "SELECT CONCAT((SELECT GROUP_CONCAT(CONCAT(id,':',created_at) ORDER BY id) FROM $target.interaction_human),'|',(SELECT GROUP_CONCAT(CONCAT(id,':',created_at) ORDER BY id) FROM $target.trip_place));"
    $snapshot = ((Invoke-TestSql $snapshotQuery) -split "`n" | Where-Object { $_ -notmatch '^WARNING:' }) -join "`n"
    Invoke-TestSql $migration | Out-Null
    Assert-TestSql $snapshotQuery $snapshot.Trim() 'repeat preserves association IDs and timestamps'
    Invoke-TestSql "DELETE FROM $target.interaction_human WHERE human_id=88; DELETE FROM $target.trip_place WHERE place_id=11; UPDATE $target.interactions SET name='Edited later' WHERE name='Synthetic trip';" | Out-Null
    Invoke-TestSql $migration | Out-Null
    Assert-TestSql "SELECT CONCAT((SELECT COUNT(*) FROM $target.interaction_human),',',(SELECT COUNT(*) FROM $target.trip_place),',',(SELECT COUNT(*) FROM $target.interactions WHERE name='Edited later'));" '2,1,1' 'repeat preserves subsequent application edits'
    Invoke-TestSql "INSERT INTO $source.citybreaks VALUES (3,NULL,'New trip','trip','2023-01-03','2023-01-01','');" | Out-Null
    Invoke-TestSql $migration 'Invalid trip dates'
    Assert-TestSql "SELECT COUNT(*) FROM $target.migration_real_meeter_trips;" '2' 'invalid dates roll back'
    Invoke-TestSql "UPDATE $source.citybreaks SET Date_stop='2023-01-04' WHERE ID=3; INSERT INTO $source.citybreak_companion(citybreak_id,human_id) VALUES (3,999);" | Out-Null
    Invoke-TestSql $migration 'Missing migrated trip participant ID'
    Assert-TestSql "SELECT COUNT(*) FROM $target.trips;" '2' 'missing participant rolls back'
    Invoke-TestSql "DELETE FROM $source.citybreak_companion WHERE citybreak_id=3; INSERT INTO $source.trip_place(trip_id,place_id) VALUES (3,999);" | Out-Null
    Invoke-TestSql $migration 'Missing migrated trip place ID'
    Invoke-TestSql "DELETE FROM $source.trip_place WHERE trip_id=3; UPDATE $source.citybreaks SET Place=REPEAT('x',129) WHERE ID=3;" | Out-Null
    Invoke-TestSql $migration 'Trip title exceeds 128'
    Assert-TestSql "SELECT COUNT(*) FROM $target.migration_real_meeter_trips;" '2' 'invalid title does not partially import'
    Invoke-TestSql "UPDATE $source.citybreaks SET Place='New trip' WHERE ID=3;" | Out-Null
    Invoke-TestSql $migration | Out-Null
    Assert-TestSql "SELECT COUNT(*) FROM $target.trips;" '3' 'later source records append once'
    Invoke-TestSql "UPDATE $target.migration_real_meeter_trips SET interaction_uuid=UUID() WHERE source_trip_id=3;" | Out-Null
    Invoke-TestSql $migration 'Migration ledger does not match existing trips'
    Assert-TestSql "SELECT COUNT(*) FROM $target.trips;" '3' 'damaged ledger rejected without duplicate import'
    Write-Host 'Trip import tests passed. No application databases were modified.'
} finally {
    try {
        if ($sourceCreated) { Invoke-TestSql "DROP DATABASE $source;" | Out-Null }
        if ($targetCreated) { Invoke-TestSql "DROP DATABASE $target;" | Out-Null }
    } finally {
        if ($hadPassword) { $env:MYSQL_PWD = $previousPassword }
        else { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
}
