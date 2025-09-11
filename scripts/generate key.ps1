# 生成 256-bit (32 字节) 随机密钥并输出 Base64
$bytes = New-Object byte[] 256
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secretBase64 = [Convert]::ToBase64String($bytes)
Write-Output $secretBase64