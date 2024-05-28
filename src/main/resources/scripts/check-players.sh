#!/bin/bash

source /etc/profile.d/valheim-environment.sh

NUM_PLAYERS=$(npx gamedig --type valheim valheim.dkarlsso.com | jq .numplayers)
uptime_minutes=$(awk '{print int($1)}' /proc/uptime)
if [ "$NUM_PLAYERS" == "0" ]; then
   echo "Server is empty"
   if [ "$uptime_minutes" -gt 1800 ]; then
     echo "The system has been running for more than 30 minutes and is empty, shutting down"
     aws autoscaling update-auto-scaling-group --auto-scaling-group-name "$AUTOSCALING_GROUP" --desired-capacity 0
   else
     echo "The system has been up for less than 30 minutes."
   fi
fi