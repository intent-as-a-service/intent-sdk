@echo off
REM ============================================================
REM 内嵌 AI 意图 SDK · 本地基础设施一键启动（Windows）
REM MySQL 5.7 实例（独立数据目录，33061） + Redis（6379）
REM ============================================================

echo [1/2] 启动 MySQL (127.0.0.1:33061)...
start "mysql-intent" /min "D:\software\installer\mysql-5.7.44-winx64\bin\mysqld.exe" --no-defaults ^
  --basedir="D:\software\installer\mysql-5.7.44-winx64" --datadir="D:\data\mysql-intent" ^
  --port=33061 --bind-address=127.0.0.1 --console

echo [2/2] 启动 Redis (127.0.0.1:6379)...
start "redis-intent" /min "D:\software\installer\redis-bin\redis-server.exe" --port 6379 --save ""

timeout /t 5 >nul
"D:\software\installer\redis-bin\redis-cli.exe" ping
"D:\software\installer\mysql-5.7.44-winx64\bin\mysql.exe" -h127.0.0.1 -P33061 -uroot -p123456 -e "SELECT 'mysql-ok';" 2>nul
echo 基础设施就绪。启动应用: java -jar ruoyi-office\yudao-server\target\yudao-server.jar --spring.profiles.active=local
