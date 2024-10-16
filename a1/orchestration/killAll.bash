#!/bin/bash
CWD="`pwd`";

# Kill the monitor
kill $(pgrep -f ./monitor)

#deleteLogFile
rm "../logging/log.txt"
echo "log file deleted"

USER=$(whoami)
# PID=$(ps aux | grep 'java' | grep "$USER" | grep -v 'grep' | awk '{print $2}')

# Kill the back end servers
for server in `cat hosts`
do
	echo $server
	ssh $server "kill -9 \$(ps aux | grep 'java' | grep \$(whoami) | grep -v 'grep' | awk '{print \$2}');" &
done

# Kill the proxy server
kill $(pgrep -u "$USER" -f URLShortner)
