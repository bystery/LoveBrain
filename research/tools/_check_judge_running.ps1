$hits = Get-CimInstance Win32_Process -Filter "Name='python.exe'" |
  Where-Object { $_.CommandLine -match 'run_p06_quality_judge\.py' -and $_.CommandLine -match '--resume-outdir' }
if ($hits) {
  foreach ($h in $hits) { Write-Output ("LIVE_JUDGE PID=" + $h.ProcessId) }
} else {
  Write-Output "NO_LIVE_JUDGE_PROCESS"
}
