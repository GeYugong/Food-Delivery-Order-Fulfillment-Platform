@echo off
rem ============================================================================
rem  Lightweight Maven launcher (no maven-wrapper.jar, no network needed)
rem
rem  IMPORTANT: keep this file PURE ASCII with CRLF line endings.
rem  Windows cmd.exe decodes .cmd files using the OEM/ANSI code page (GBK on
rem  Chinese Windows). A UTF-8 file with Chinese comments is mis-decoded and
rem  the batch parser breaks: "rem" lines swallow the following command, so
rem  "setlocal" is parsed as "tlocal" and "%MAVEN_HOME%" as "AVEN_HOME*".
rem
rem  Search order:
rem    1) %MAVEN_HOME%\bin\mvn.cmd
rem    2) %M2_HOME%\bin\mvn.cmd
rem    3) Maven Wrapper cache: %USERPROFILE%\.m2\wrapper\dists\apache-maven-*
rem    4) mvn on PATH
rem
rem  Usage is identical to mvn, e.g.:  mvnw.cmd clean package
rem ============================================================================
setlocal enabledelayedexpansion

rem Resolve the Maven Wrapper cache root once, so the search below stays readable.
set "WRAPPER_DISTS=%USERPROFILE%\.m2\wrapper\dists"

if not "%MAVEN_HOME%"=="" (
    if exist "%MAVEN_HOME%\bin\mvn.cmd" (
        call "%MAVEN_HOME%\bin\mvn.cmd" %*
        exit /b %ERRORLEVEL%
    )
)

if not "%M2_HOME%"=="" (
    if exist "%M2_HOME%\bin\mvn.cmd" (
        call "%M2_HOME%\bin\mvn.cmd" %*
        exit /b %ERRORLEVEL%
    )
)

if exist "%WRAPPER_DISTS%" (
    for /d %%V in ("%WRAPPER_DISTS%\apache-maven-*") do (
        if exist "%%~fV\bin\mvn.cmd" (
            set "MVN_FOUND=%%~fV\bin\mvn.cmd"
            goto :found
        )
        for /d %%H in ("%%~fV\*") do (
            if exist "%%~fH\bin\mvn.cmd" (
                set "MVN_FOUND=%%~fH\bin\mvn.cmd"
                goto :found
            )
        )
    )
)

where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

echo [mvnw] Maven was not found. Choose one of these options:
echo [mvnw]   1) Install Maven 3.9+ and set MAVEN_HOME
echo [mvnw]   2) Open this project in IntelliJ IDEA / Eclipse and use its bundled Maven
echo [mvnw]   3) Build with javac + java (see README.md, section 1, "no global mvn")
exit /b 1

rem ---------------------------------------------------------------------------
rem  Found a cached Maven distribution: hand over all arguments to it.
rem  Layout searched:  <versionDir>\bin\mvn.cmd   (flat install)
rem                    <versionDir>\<hash>\bin\mvn.cmd  (wrapper cache)
rem ---------------------------------------------------------------------------
:found
call "%MVN_FOUND%" %*
exit /b %ERRORLEVEL%
