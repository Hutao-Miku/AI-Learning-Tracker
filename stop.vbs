Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptPath = WScript.ScriptFullName
baseDir = fso.GetParentFolderName(scriptPath)
pidFile = fso.BuildPath(baseDir, "run.pid")

backendTitle = "StudyTrace_Backend"
frontendTitle = "StudyTrace_Frontend"

' 按 PID 精准关闭：先校验该 PID 的窗口标题确属本项目，避免误杀 PID 被复用后的其他程序
Sub KillByPidSafe(pid, expectedTitle)
    If pid = "" Then Exit Sub
    Dim tmpFile, cmdLine, output, ok
    ok = False
    On Error Resume Next
    tmpFile = fso.BuildPath(fso.GetSpecialFolder(2), "st_chk.tmp")
    cmdLine = "cmd /c tasklist /FI ""WINDOWTITLE eq " & expectedTitle & """ /FI ""PID eq " & pid & """ /FO CSV /NH > """ & tmpFile & """"
    WshShell.Run cmdLine, 0, True
    If fso.FileExists(tmpFile) Then
        output = fso.OpenTextFile(tmpFile, 1).ReadAll()
        fso.DeleteFile tmpFile
        If InStr(1, output, "INFO:", 1) = 0 And Trim(output) <> "" Then ok = True
    End If
    If ok Then
        WshShell.Run "taskkill /T /F /PID " & pid, 0, True
    End If
    On Error GoTo 0
End Sub

' 按窗口标题关闭（兜底方案，只关本项目专属窗口，绝不动端口、绝不通杀）
Sub KillByTitle(title)
    Dim tmpFile, cmdLine, output, lines, line, parts
    On Error Resume Next
    tmpFile = fso.BuildPath(fso.GetSpecialFolder(2), "st_kill.tmp")
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
                        Dim pid
                        pid = Replace(Replace(parts(1), """", ""), " ", "")
                        WshShell.Run "taskkill /T /F /PID " & pid, 0, True
                    End If
                End If
            Next
        End If
    End If
    On Error GoTo 0
End Sub

' 主流程：优先读取 run.pid 精准关闭；无 PID 文件则直接按标题关闭
If fso.FileExists(pidFile) Then
    Dim ts, content, lines, line, kv, key, val
    Set ts = fso.OpenTextFile(pidFile, 1)
    content = ts.ReadAll()
    ts.Close
    lines = Split(content, vbCrLf)
    For Each line In lines
        line = Trim(line)
        If InStr(line, "=") > 0 Then
            kv = Split(line, "=")
            key = Trim(kv(0))
            val = Trim(kv(1))
            If key = "backend" Then KillByPidSafe val, backendTitle
            If key = "frontend" Then KillByPidSafe val, frontendTitle
        End If
    Next
    fso.DeleteFile pidFile
Else
    KillByTitle backendTitle
    KillByTitle frontendTitle
End If

' 兜底：无论是否存在 PID 文件，再按标题关一次（只关本项目专属窗口）
KillByTitle backendTitle
KillByTitle frontendTitle

MsgBox "已尝试安全关闭本项目的前后端进程。", vbInformation, "关闭完成"
