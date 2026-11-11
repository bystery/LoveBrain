$procs = Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -match 'run_p06_quality_judge' }
foreach ($p in $procs) {
  Stop-Process -Id $p.ProcessId -Force
  Write-Output ("KILLED " + $p.ProcessId)
}
if (-not $procs) { Write-Output "NO_JUDGE_PROCESS" }
