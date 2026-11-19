#!/bin/bash

OUT=${1:-load-tests/results/host-resources.csv}

echo "timestamp,cpu_percent,memory_used_mib,memory_available_mib" > "$OUT"

read_cpu() {
    read cpu user nice system idle iowait irq softirq steal rest < /proc/stat

    IDLE_ALL=$((idle + iowait))
    TOTAL=$((user + nice + system + idle + iowait + irq + softirq + steal))
}

read_cpu
PREV_IDLE=$IDLE_ALL
PREV_TOTAL=$TOTAL

while true; do
    sleep 1

    read_cpu

    DELTA_IDLE=$((IDLE_ALL - PREV_IDLE))
    DELTA_TOTAL=$((TOTAL - PREV_TOTAL))

    CPU=$(awk -v total="$DELTA_TOTAL" -v idle="$DELTA_IDLE" \
        'BEGIN {
            if (total > 0)
                printf "%.2f", (total-idle)*100/total;
            else
                printf "0.00"
        }')

    MEM_TOTAL=$(awk '/MemTotal:/ {print $2}' /proc/meminfo)
    MEM_AVAILABLE=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)

    MEM_USED=$((MEM_TOTAL - MEM_AVAILABLE))

    MEM_USED_MIB=$(awk -v kb="$MEM_USED" 'BEGIN {printf "%.2f", kb/1024}')
    MEM_AVAILABLE_MIB=$(awk -v kb="$MEM_AVAILABLE" 'BEGIN {printf "%.2f", kb/1024}')

    echo "$(date -Iseconds),$CPU,$MEM_USED_MIB,$MEM_AVAILABLE_MIB" >> "$OUT"

    PREV_IDLE=$IDLE_ALL
    PREV_TOTAL=$TOTAL
done
