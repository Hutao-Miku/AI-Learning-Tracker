Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptPath = WScript.ScriptFullName
baseDir = fso.GetParentFolderName(scriptPath)
backendDir = fso.BuildPath(baseDir, "backend")
frontendDir = fso.BuildPath(baseDir, "frontend")

backendTitle = "StudyTrace_Backend"
frontendTitle = "StudyTrace_Frontend"

' 启动后端：隐藏窗口，强制端口 8080，并打上本项目专属窗口标题
WshShell.Run "cmd /c title " & backendTitle & " && cd /d """ & backendDir & """ && mvn spring-boot:run -Dspring-boot.run.arguments="""--server.port=8080"""", 0, False
' 启动前端：隐藏窗口，打上本项目专属窗口标题
WshShell.Run "cmd /c title " & frontendTitle & " && cd /d """ & frontendDir & """ && npm run dev", 0, False

' 等窗口标题生效后再抓取 PID
WScript.Sleep 2000

backendPid = GetPidByTitle(backendTitle)
frontendPid = GetPidByTitle(frontendTitle)

' 把本项目相关进程的 PID 写入 run.pid，供 stop.vbs 精准关闭（绝不按端口盲杀）
Set pidFile = fso.CreateTextFile(fso.BuildPath(baseDir, "run.pid"), True)
pidFile.WriteLine "backend=" & backendPid
pidFile.WriteLine "frontend=" & frontendPid
pidFile.Close

' 健康探活：后端就绪后自动打开浏览器
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
    MsgBox "后端启动失败，请检查 start.bat 输出。", vbExclamation, "启动提示"
End If

' 通过专属窗口标题查找进程 PID
Function GetPidByTitle(title)
    Dim pid, tmpFile, cmdLine, output, lines, line, parts
    pid = ""
    On Error Resume Next
    tmpFile = fso.BuildPath(fso.GetSpecialFolder(2), "st_pid_" & Replace(title, " ", "_") & ".tmp")
    cmdLine = "cmd /c tasklist /FI ""WINDOWTITLE eq " & title & """ /FO CSV /NH > """ & tmpFile & """"
    WshShell.Run cmdLine, 0, True
    If fso.FileExists(tmpFile) Then
        output = fso.OpenTextFile(tmpFile, 1).ReadAll()
        fso.DeleteFile tmpFile
        If InStr(1, output, "INFO:", 1) = 0 Then
            lines = Split(output, vbCrLf)
            For Each line In lines
                line = Trim(line)
                If Len(line) > 0 Then
                    parts = Split(line, ",")
                    If UBound(parts) >= 1 Then
                        pid = Replace(Replace(parts(1), """", ""), " ", "")
                        Exit For
                    End If
                End If
            Next
        End If
    End If
    GetPidByTitle = pid
End Function
