#!/bin/bash
USER=$(whoami)
server=($1)

ssh $server "kill -9 \$(ps aux | grep 'java' | grep \$(whoami) | grep -v 'grep' | awk '{print \$2}');" &
