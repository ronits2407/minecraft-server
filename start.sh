#!/bin/bash
cd "$(dirname "$0")"
if tmux has-session -t mc 2>/dev/null; then
    echo "Server is already running in tmux session \"mc\"."
else
    echo "Starting server in tmux session \"mc\"..."
    tmux new-session -d -s mc "java -Xms4G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -Dsun.net.client.defaultConnectTimeout=1000 -Dsun.net.client.defaultReadTimeout=1000 -Dpaper.playerconnection.keepalive=60 -Dlog4j.configurationFile=log4j2.xml -jar server.jar nogui"
    echo "Server started! Use ./console.sh to attach to the console."
fi
