$ErrorActionPreference = 'Stop'
# Entirely disposable database. No application credentials or volumes are used.
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
$network = "auction-tests-$suffix"
$database = "auction-db-$suffix"
docker build -f "$PSScriptRoot/Dockerfile.auction-tests" -t margomarket-auction-tests "$PSScriptRoot/.."
if ($LASTEXITCODE -ne 0) { throw 'Backend unit tests or compilation failed' }
docker network create $network | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not create test network' }
try {
    docker run --rm -d --name $database --network $network -e POSTGRES_USER=auction_test -e POSTGRES_PASSWORD=auction_test -e POSTGRES_DB=auction_test postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable PostgreSQL' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $database pg_isready -U auction_test -d auction_test 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'PostgreSQL did not become ready' }
    docker run --rm --network $network -e "AUCTION_TEST_DATABASE_URL=jdbc:postgresql://${database}:5432/auction_test" margomarket-auction-tests
    if ($LASTEXITCODE -ne 0) { throw 'Auction integration tests failed' }
} finally {
    docker rm -f $database 2>$null | Out-Null
    docker network rm $network 2>$null | Out-Null
}
