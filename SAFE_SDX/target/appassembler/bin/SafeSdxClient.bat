@REM ----------------------------------------------------------------------------
@REM  Copyright 2001-2006 The Apache Software Foundation.
@REM
@REM  Licensed under the Apache License, Version 2.0 (the "License");
@REM  you may not use this file except in compliance with the License.
@REM  You may obtain a copy of the License at
@REM
@REM       http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM  See the License for the specific language governing permissions and
@REM  limitations under the License.
@REM ----------------------------------------------------------------------------
@REM
@REM   Copyright (c) 2001-2006 The Apache Software Foundation.  All rights
@REM   reserved.

@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
set BASEDIR=%~dp0\..

:repoSetup
set REPO=


if "%JAVACMD%"=="" set JAVACMD=java

if "%REPO%"=="" set REPO=%BASEDIR%\repo

set CLASSPATH="%BASEDIR%"\etc;"%REPO%"\org\renci\ahab\libndl\0.1.1\libndl-0.1.1.jar;"%REPO%"\orca\ndl\5.0.2\ndl-5.0.2.jar;"%REPO%"\org\apache\jena\jena-tdb\1.0.2\jena-tdb-1.0.2.jar;"%REPO%"\org\apache\jena\jena-arq\2.11.2\jena-arq-2.11.2.jar;"%REPO%"\com\github\jsonld-java\jsonld-java\0.4\jsonld-java-0.4.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-core\2.3.3\jackson-core-2.3.3.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-databind\2.3.3\jackson-databind-2.3.3.jar;"%REPO%"\org\apache\jena\jena-core\2.11.2\jena-core-2.11.2.jar;"%REPO%"\org\apache\jena\jena-iri\1.0.2\jena-iri-1.0.2.jar;"%REPO%"\xerces\xercesImpl\2.11.0\xercesImpl-2.11.0.jar;"%REPO%"\xml-apis\xml-apis\1.4.01\xml-apis-1.4.01.jar;"%REPO%"\javax\xml\jaxb-xjc\2.0EA3\jaxb-xjc-2.0EA3.jar;"%REPO%"\edu\washington\sig\gleen\0.6.2\gleen-0.6.2.jar;"%REPO%"\org\slf4j\slf4j-log4j12\1.7.5\slf4j-log4j12-1.7.5.jar;"%REPO%"\net\jwhoisserver\jwhoisserver\0.4.1.1\jwhoisserver-0.4.1.1.jar;"%REPO%"\org\ini4j\ini4j\0.5.4\ini4j-0.5.4.jar;"%REPO%"\log4j\log4j\1.2.13\log4j-1.2.13.jar;"%REPO%"\org\slf4j\jcl-over-slf4j\1.7.5\jcl-over-slf4j-1.7.5.jar;"%REPO%"\org\slf4j\slf4j-api\1.7.5\slf4j-api-1.7.5.jar;"%REPO%"\net\sf\jung\jung-api\2.0.1\jung-api-2.0.1.jar;"%REPO%"\net\sourceforge\collections\collections-generic\4.01\collections-generic-4.01.jar;"%REPO%"\net\sf\jung\jung-3d\2.0.1\jung-3d-2.0.1.jar;"%REPO%"\java3d\vecmath\1.3.1\vecmath-1.3.1.jar;"%REPO%"\java3d\j3d-core\1.3.1\j3d-core-1.3.1.jar;"%REPO%"\net\sf\jung\jung-algorithms\2.0.1\jung-algorithms-2.0.1.jar;"%REPO%"\colt\colt\1.2.0\colt-1.2.0.jar;"%REPO%"\concurrent\concurrent\1.3.4\concurrent-1.3.4.jar;"%REPO%"\net\sf\jung\jung-graph-impl\2.0.1\jung-graph-impl-2.0.1.jar;"%REPO%"\net\sf\jung\jung-jai\2.0.1\jung-jai-2.0.1.jar;"%REPO%"\net\sf\jung\jung-visualization\2.0.1\jung-visualization-2.0.1.jar;"%REPO%"\net\sf\jung\jung-io\2.0.1\jung-io-2.0.1.jar;"%REPO%"\org\codehaus\woodstox\wstx-asl\3.2.6\wstx-asl-3.2.6.jar;"%REPO%"\stax\stax-api\1.0.1\stax-api-1.0.1.jar;"%REPO%"\net\java\dev\swing-layout\swing-layout\1.0.2\swing-layout-1.0.2.jar;"%REPO%"\commons-lang\commons-lang\2.5\commons-lang-2.5.jar;"%REPO%"\com\google\guava\guava\14.0.1\guava-14.0.1.jar;"%REPO%"\com\google\code\gson\gson\2.2.4\gson-2.2.4.jar;"%REPO%"\org\apache\xmlrpc\xmlrpc-client\3.1.3\xmlrpc-client-3.1.3.jar;"%REPO%"\org\apache\xmlrpc\xmlrpc-common\3.1.3\xmlrpc-common-3.1.3.jar;"%REPO%"\org\apache\ws\commons\util\ws-commons-util\1.0.2\ws-commons-util-1.0.2.jar;"%REPO%"\junit\junit\3.8.1\junit-3.8.1.jar;"%REPO%"\org\bouncycastle\bcprov-jdk15on\1.50\bcprov-jdk15on-1.50.jar;"%REPO%"\org\bouncycastle\bcprov-ext-jdk15on\1.50\bcprov-ext-jdk15on-1.50.jar;"%REPO%"\org\bouncycastle\bcpkix-jdk15on\1.50\bcpkix-jdk15on-1.50.jar;"%REPO%"\orca\core\util\5.0.2\util-5.0.2.jar;"%REPO%"\commons-dbcp\commons-dbcp\1.3\commons-dbcp-1.3.jar;"%REPO%"\commons-pool\commons-pool\1.5.4\commons-pool-1.5.4.jar;"%REPO%"\mysql\mysql-connector-java\5.1.17\mysql-connector-java-5.1.17.jar;"%REPO%"\jabac\jabac\1.3.1\jabac-1.3.1.jar;"%REPO%"\collections-generic\collections-generic\4.01\collections-generic-4.01.jar;"%REPO%"\commons-httpclient\commons-httpclient\3.1\commons-httpclient-3.1.jar;"%REPO%"\org\ektorp\ektorp\1.4.2\ektorp-1.4.2.jar;"%REPO%"\org\apache\httpcomponents\httpclient-cache\4.2.3\httpclient-cache-4.2.3.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-annotations\2.4.1\jackson-annotations-2.4.1.jar;"%REPO%"\commons-io\commons-io\2.0.1\commons-io-2.0.1.jar;"%REPO%"\asm\asm\3.3.1\asm-3.3.1.jar;"%REPO%"\com\sun\jersey\jersey-bundle\1.19\jersey-bundle-1.19.jar;"%REPO%"\javax\ws\rs\jsr311-api\1.1.1\jsr311-api-1.1.1.jar;"%REPO%"\org\json\json\20140107\json-20140107.jar;"%REPO%"\com\sun\jersey\jersey-server\1.19\jersey-server-1.19.jar;"%REPO%"\com\sun\jersey\jersey-core\1.19\jersey-core-1.19.jar;"%REPO%"\org\renci\ahab\libtransport\0.1.4\libtransport-0.1.4.jar;"%REPO%"\com\jcraft\jsch\0.1.53\jsch-0.1.53.jar;"%REPO%"\org\apache\logging\log4j\log4j-core\2.3\log4j-core-2.3.jar;"%REPO%"\org\apache\logging\log4j\log4j-api\2.3\log4j-api-2.3.jar;"%REPO%"\org\apache\httpcomponents\httpclient\4.5.3\httpclient-4.5.3.jar;"%REPO%"\org\apache\httpcomponents\httpcore\4.4.6\httpcore-4.4.6.jar;"%REPO%"\commons-logging\commons-logging\1.2\commons-logging-1.2.jar;"%REPO%"\commons-codec\commons-codec\1.9\commons-codec-1.9.jar;"%REPO%"\safe\sdx\0.1-SNAPSHOT\sdx-0.1-SNAPSHOT.jar

set ENDORSED_DIR=
if NOT "%ENDORSED_DIR%" == "" set CLASSPATH="%BASEDIR%"\%ENDORSED_DIR%\*;%CLASSPATH%

if NOT "%CLASSPATH_PREFIX%" == "" set CLASSPATH=%CLASSPATH_PREFIX%;%CLASSPATH%

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %JAVA_OPTS%  -classpath %CLASSPATH% -Dapp.name="SafeSdxClient" -Dapp.repo="%REPO%" -Dapp.home="%BASEDIR%" -Dbasedir="%BASEDIR%" safe.sdx.SdxClient %CMD_LINE_ARGS%
if %ERRORLEVEL% NEQ 0 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=%ERRORLEVEL%

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@REM If error code is set to 1 then the endlocal was done already in :error.
if %ERROR_CODE% EQU 0 @endlocal


:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
