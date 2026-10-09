Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptPath = WScript.ScriptFullName
baseDir = fso.GetParentFolderName(scriptPath)
backendDir = fso.BuildPath(baseDir, "backend")
frontendDir = fso.BuildPath(baseDir, "frontend")

WshShell.Run "cmd /c cd /d """ & backendDir & """ && mvn spring-boot:run", 0, False
WshShell.Run "cmd /c cd /d """ & frontendDir & """ && npm run dev", 0, False

Dim http, ok, elapsed
ok = False
elapsed = 0
Do While elapsed < 30
    On Error Resume Next
    Set http = CreateObject("WinHttp.WinHttpRequest.5.1")
    http.SetProxy 1
    http.SetTimeouts 3000, 1000, 1000, 1000
    http.Open "GET", "http://localhost:8080/api/hello", False
    http.Send
    If Err.Number = 0 Then
        If http.Status >= 200 And http.Status < 400 Then
            ok = True
        End If
    End If
    On Error GoTo 0
    If ok Then Exit Do
    WScript.Sleep 1000
    elapsed = elapsed + 1
Loop

If ok Then
    WshShell.Run "http://localhost:5173", 1, False
Else
    MsgBox "启动可能失败，请运行 start.bat 排查。", vbExclamation, "启动提示"
End If
