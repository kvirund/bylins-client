' Запуск Bylins Client без окна консоли.
' Используется ярлыком на панели задач: цель ярлыка — wscript.exe (настоящий
' exe), поэтому Windows разрешает закрепление, а консоль сборки не мешает.
Option Explicit

Dim sh, fso, projectDir, cmd
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

projectDir = fso.GetParentFolderName(WScript.ScriptFullName)
sh.CurrentDirectory = projectDir

' BYLINS_NOPAUSE=1 — не ждать нажатия клавиши при ошибке (окна всё равно не видно)
cmd = "cmd /c set BYLINS_NOPAUSE=1&& """ & projectDir & "\bylins.bat"""

' 0 — скрытое окно, False — не ждать завершения
sh.Run cmd, 0, False
