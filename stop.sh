#!/bin/bash
if tmux has-session -t mc 2>/dev/null; then
    echo "Stopping server gracefully..."
    tmux send-keys -t mc "stop" C-m
else
    echo "Server is not running."
fi
