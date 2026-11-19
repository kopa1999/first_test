#!/bin/bash

OUT=${1:-load-tests/results/resources.csv}

echo "timestamp,container,cpu,ram,ram_percent" > "$OUT"

while true; do
    TIMESTAMP=$(date -Iseconds)

    IDS=$(sudo docker ps \
        --filter "label=com.docker.compose.project=product-service-prototype" \
        -q)

    if [ -n "$IDS" ]; then
        sudo docker stats --no-stream \
            --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}" \
            $IDS |
        while read -r LINE; do
            echo "$TIMESTAMP,$LINE"
        done >> "$OUT"
    fi

    sleep 1
done
