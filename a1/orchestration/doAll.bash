#!/bin/bash
CWD="`pwd`";

# Kill all previously opened servers
./killAll.bash

# Start the monitor
./monitor &

loggingFile="../logging/log.txt"
mkdir ../logging 2> /dev/null
if [ ! -f "$loggingFile" ]; then
    touch $loggingFile
    echo "File '$loggingFile' created."
else
    echo "File '$loggingFile' already exists."
fi

# Start the back end servers
for server in `cat hosts`
do
	echo $server
	ssh $server "cd \"$CWD/../serverSqlite\"; bash runit.bash;" &
done

# Start the reverse proxy
cd $CWD/../serverFile
./runit.bash
