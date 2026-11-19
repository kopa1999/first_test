# first_test

General:
- sudo docker ps -> show all running containers
- sudo docker compose down -> turn off compose stack
- sudo docker inspect order-service   --format '{{range .Config.Env}}{{println .}}{{end}}'   | grep JAVA_TOOL_OPTIONS -> check java agent status
- sudo docker ps --filter name=obi -> check obi status (sudo docker rm -f obi)
- ./load-tests/reset-test-db.sh -> reset db data
- sudo docker run --rm   --network product-service-prototype_default   --mount type=bind,source="$(realpath load-tests)",target=/scripts   grafana/k6 run   -e USER_ID=1   -e PRODUCT_ID=1   -e RATE=200   -e DURATION=30s   /scripts/s1-perf.js -> warm up

Monitoring:
- ./load-tests/monitor-resources.sh load-tests/results/*-rps-resources.csv -> resources monitoring (inside docker containers)
- ./load-tests/monitor-host.sh load-tests/results/*-rps-host.csv -> host monitoring 

Baseline:
- sudo docker compose   -f docker-compose.yml   -f docker-compose-baseline.yml   up -d --remove-orphans -> start without java agent
- warm up
- reset DB
- start monitorings
- sudo docker run --rm   --network product-service-prototype_default   --mount type=bind,source="$(realpath load-tests)",target=/scripts   grafana/k6 run   -e USER_ID=1   -e PRODUCT_ID=1   -e RATE=200   -e DURATION=60s   /scripts/s1-perf.js   2>&1 | tee load-tests/results/baseline-*rps_*.txt -> start testing

Java Agent:
- sudo docker compose up -d -> start with java agent
- warm up
- reset DB
- start monitorings
- sudo docker run --rm   --network product-service-prototype_default   --mount type=bind,source="$(realpath load-tests)",target=/scripts   grafana/k6 run   -e USER_ID=1   -e PRODUCT_ID=1   -e RATE=200   -e DURATION=60s   /scripts/s1-perf.js   2>&1 | tee load-tests/results/javaagent-*rps_*.txt -> start testing

OBI:
- sudo docker compose   -f docker-compose.yml   -f docker-compose-obi.yml   up -d -> start with OBI eBPF
- warm up
- reset DB
- start monitorings
- sudo docker run --rm   --network product-service-prototype_default   --mount type=bind,source="$(realpath load-tests)",target=/scripts   grafana/k6 run   -e USER_ID=1   -e PRODUCT_ID=1   -e RATE=200   -e DURATION=60s   /scripts/s1-perf.js   2>&1 | tee load-tests/results/OBI-*rps_*.txt -> start testing

eBPF:
- start baseline
- ORDER_PID=$(sudo docker inspect --format '{{.State.Pid}}' order-service)  -> save actual order process id
- echo "$ORDER_PID"   ->  to check if it works
- sudo ./tcp_monitor.py --pid "$ORDER_PID"  -> start eBPF monitoring(without saving)
- warm up
- reset DB
- start monitorings
- sudo docker run --rm   --network product-service-prototype_default   --mount type=bind,source="$(realpath load-tests)",target=/scripts   grafana/k6 run   -e USER_ID=1   -e PRODUCT_ID=1   -e RATE=200   -e DURATION=60s   /scripts/s1-perf.js   2>&1 | tee load-tests/results/eBPF-*rps_*.txt -> start testing


