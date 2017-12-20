@echo off

rem : This batch file is intended to be located in the same folder as tle.exe and run
rem : from a command prompt with current directory set to folder containing tle.exe.
rem : This allows access to all logging events sent to the console while TLE is running.

rem : Look in registry for current Java version
FOR /F "tokens=2*" %%a IN ('REG QUERY "HKEY_LOCAL_MACHINE\Software\JavaSoft\Java Runtime Environment" /v CurrentVersion') DO set "CurVer=%%b"

rem : Look in registry for current Java path
FOR /F "tokens=2*" %%a IN ('REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment\%CurVer%" /v JavaHome') DO set "JavaPath=%%b"

rem : Launch TLE using current Java path
"%JavaPath%\bin\java.exe" -classpath "domecasting.jar;.;commons-io-2.5.jar;log4j-api-2.9.1.jar;log4j-core-2.9.1.jar" com.spitzinc.domecasting.client.ClientApplication

pause