#!/usr/bin/env python3

from bcc import BPF
import argparse
import glob
import os
import re
import time


SERVICES = {
    1: "USER:8081",
    2: "PRODUCT:8082",
    3: "SHOPPING:8083",
    4: "PAYMENT:8084",
    5: "POSTGRES:5432",
}

PORT_TO_SERVICE = {
    8081: 1,
    8082: 2,
    8083: 3,
    8084: 4,
    5432: 5,
}


parser = argparse.ArgumentParser()
parser.add_argument("--pid", type=int, required=True)
args = parser.parse_args()

TARGET_PID = args.pid


program = r"""
#include <uapi/linux/ptrace.h>

struct fd_key {
    u32 tgid;
    int fd;
};

struct sockaddr_port_min {
    u16 family;
    u16 port;
};


/* ============================================================
 * Existing TCP byte counters
 * ============================================================ */

BPF_HASH(active_send, u64, u8);
BPF_HASH(active_recv, u64, u8);

BPF_HASH(sent_bytes, u32, u64);
BPF_HASH(recv_bytes, u32, u64);


/* ============================================================
 * Socket/service mapping
 * ============================================================ */

BPF_HASH(fd_service, struct fd_key, u32, 4096);

BPF_HASH(request_start, struct fd_key, u64, 4096);

BPF_HASH(connect_service_by_tid, u64, u32, 1024);

BPF_HASH(write_fd_by_tid, u64, struct fd_key, 4096);
BPF_HASH(read_fd_by_tid, u64, struct fd_key, 4096);


/* ============================================================
 * Per-service counters
 *
 * index:
 * 1 user
 * 2 product
 * 3 shopping
 * 4 payment
 * 5 postgres
 * ============================================================ */

BPF_ARRAY(request_count, u64, 6);

BPF_ARRAY(latency_count, u64, 6);
BPF_ARRAY(latency_ns_total, u64, 6);
BPF_ARRAY(latency_ns_max, u64, 6);

BPF_ARRAY(connect_attempts, u64, 6);
BPF_ARRAY(connect_errors, u64, 6);

BPF_ARRAY(io_errors, u64, 6);


static __always_inline u32 current_tgid(void)
{
    return bpf_get_current_pid_tgid() >> 32;
}


static __always_inline u16 swap16(u16 x)
{
    return (u16)((x >> 8) | (x << 8));
}


static __always_inline u32 service_from_port(u16 port)
{
    if (port == 8081)
        return 1;

    if (port == 8082)
        return 2;

    if (port == 8083)
        return 3;

    if (port == 8084)
        return 4;

    if (port == 5432)
        return 5;

    return 0;
}


static __always_inline void inc_request(u32 service)
{
    u64 *v = request_count.lookup(&service);

    if (v)
        __sync_fetch_and_add(v, 1);
}


static __always_inline void inc_latency_count(u32 service)
{
    u64 *v = latency_count.lookup(&service);

    if (v)
        __sync_fetch_and_add(v, 1);
}


static __always_inline void add_latency(u32 service, u64 delta)
{
    u64 *total = latency_ns_total.lookup(&service);

    if (total)
        __sync_fetch_and_add(total, delta);

    u64 *maxv = latency_ns_max.lookup(&service);

    if (maxv && delta > *maxv)
        *maxv = delta;
}


static __always_inline void inc_connect_attempt(u32 service)
{
    u64 *v = connect_attempts.lookup(&service);

    if (v)
        __sync_fetch_and_add(v, 1);
}


static __always_inline void inc_connect_error(u32 service)
{
    u64 *v = connect_errors.lookup(&service);

    if (v)
        __sync_fetch_and_add(v, 1);
}


static __always_inline void inc_io_error(u32 service)
{
    u64 *v = io_errors.lookup(&service);

    if (v)
        __sync_fetch_and_add(v, 1);
}


/* ============================================================
 * TCP TX/RX
 * ============================================================ */

int trace_tcp_sendmsg(struct pt_regs *ctx)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;

    u64 id = bpf_get_current_pid_tgid();

    u8 one = 1;

    active_send.update(&id, &one);

    return 0;
}


int trace_tcp_sendmsg_ret(struct pt_regs *ctx)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;

    u64 id = bpf_get_current_pid_tgid();

    u8 *active = active_send.lookup(&id);

    if (!active)
        return 0;

    int ret = (int)PT_REGS_RC(ctx);

    if (ret > 0) {

        u64 zero = 0;

        u64 *value =
            sent_bytes.lookup_or_init(&tgid, &zero);

        if (value)
            __sync_fetch_and_add(
                value,
                (u64)ret
            );
    }

    active_send.delete(&id);

    return 0;
}


int trace_tcp_recvmsg(struct pt_regs *ctx)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;

    u64 id = bpf_get_current_pid_tgid();

    u8 one = 1;

    active_recv.update(&id, &one);

    return 0;
}


int trace_tcp_recvmsg_ret(struct pt_regs *ctx)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;

    u64 id = bpf_get_current_pid_tgid();

    u8 *active = active_recv.lookup(&id);

    if (!active)
        return 0;

    int ret = (int)PT_REGS_RC(ctx);

    if (ret > 0) {

        u64 zero = 0;

        u64 *value =
            recv_bytes.lookup_or_init(
                &tgid,
                &zero
            );

        if (value)
            __sync_fetch_and_add(
                value,
                (u64)ret
            );
    }

    active_recv.delete(&id);

    return 0;
}


/* ============================================================
 * CONNECT
 * ============================================================ */

TRACEPOINT_PROBE(syscalls, sys_enter_connect)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    struct sockaddr_port_min sa = {};


if (!args->uservaddr)
    return 0;


if (bpf_probe_read_user(
        &sa,
        sizeof(sa),
        (void *)args->uservaddr) != 0)
    return 0;


/*
 * AF_INET  = 2
 * AF_INET6 = 10
 *
 * We only need family + destination port.
 * These are the first fields in both
 * sockaddr_in and sockaddr_in6.
 */

if (
    sa.family != 2 &&
    sa.family != 10
)
    return 0;


u16 port = swap16(sa.port);

    u32 service =
        service_from_port(port);


    if (service == 0)
        return 0;


    struct fd_key key = {};

    key.tgid = tgid;
    key.fd = args->fd;


    fd_service.update(
        &key,
        &service
    );


    u64 id =
        bpf_get_current_pid_tgid();


    connect_service_by_tid.update(
        &id,
        &service
    );


    inc_connect_attempt(service);


    return 0;
}


TRACEPOINT_PROBE(syscalls, sys_exit_connect)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    u64 id =
        bpf_get_current_pid_tgid();


    u32 *service =
        connect_service_by_tid.lookup(&id);


    if (!service)
        return 0;


    long ret = args->ret;


    /*
     * Java NIO often uses non-blocking connect.
     *
     * -115 = EINPROGRESS
     * -114 = EALREADY
     *
     * These are NOT errors for our purposes.
     */

    if (
        ret < 0 &&
        ret != -115 &&
        ret != -114
    ) {
        inc_connect_error(*service);
    }


    connect_service_by_tid.delete(&id);


    return 0;
}


/* ============================================================
 * WRITE
 *
 * Successful write = beginning of a possible round trip.
 * ============================================================ */

static __always_inline int
remember_write_fd(int fd)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    struct fd_key key = {};

    key.tgid = tgid;
    key.fd = fd;


    u32 *service =
        fd_service.lookup(&key);


    if (!service)
        return 0;


    u64 id =
        bpf_get_current_pid_tgid();


    write_fd_by_tid.update(
        &id,
        &key
    );


    return 0;
}


static __always_inline int
finish_write(long ret)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    u64 id =
        bpf_get_current_pid_tgid();


    struct fd_key *key =
        write_fd_by_tid.lookup(&id);


    if (!key)
        return 0;


    u32 *service =
        fd_service.lookup(key);


    if (service) {

        if (ret > 0) {

            u64 *existing =
                request_start.lookup(key);


            /*
             * Do not overwrite if this request
             * was split into multiple writes.
             */

            if (!existing) {

                u64 now =
                    bpf_ktime_get_ns();


                request_start.update(
                    key,
                    &now
                );


                inc_request(*service);
            }

        } else if (
            ret < 0 &&
            ret != -11 &&
            ret != -4
        ) {

            /*
             * Ignore:
             *
             * -11 EAGAIN / EWOULDBLOCK
             * -4  EINTR
             */

            inc_io_error(*service);

            request_start.delete(key);
        }
    }


    write_fd_by_tid.delete(&id);


    return 0;
}


TRACEPOINT_PROBE(syscalls, sys_enter_write)
{
    return remember_write_fd(
        args->fd
    );
}


TRACEPOINT_PROBE(syscalls, sys_exit_write)
{
    return finish_write(
        args->ret
    );
}


TRACEPOINT_PROBE(syscalls, sys_enter_writev)
{
    return remember_write_fd(
        args->fd
    );
}


TRACEPOINT_PROBE(syscalls, sys_exit_writev)
{
    return finish_write(
        args->ret
    );
}

TRACEPOINT_PROBE(syscalls, sys_enter_sendto)
{
    return remember_write_fd(args->fd);
}

TRACEPOINT_PROBE(syscalls, sys_exit_sendto)
{
    return finish_write(args->ret);
}


TRACEPOINT_PROBE(syscalls, sys_enter_sendmsg)
{
    return remember_write_fd(args->fd);
}

TRACEPOINT_PROBE(syscalls, sys_exit_sendmsg)
{
    return finish_write(args->ret);
}


/* ============================================================
 * READ
 *
 * First successful read after write
 * = round-trip latency proxy.
 * ============================================================ */

static __always_inline int
remember_read_fd(int fd)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    struct fd_key key = {};

    key.tgid = tgid;
    key.fd = fd;


    u32 *service =
        fd_service.lookup(&key);


    if (!service)
        return 0;


    u64 id =
        bpf_get_current_pid_tgid();


    read_fd_by_tid.update(
        &id,
        &key
    );


    return 0;
}


static __always_inline int
finish_read(long ret)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    u64 id =
        bpf_get_current_pid_tgid();


    struct fd_key *key =
        read_fd_by_tid.lookup(&id);


    if (!key)
        return 0;


    u32 *service =
        fd_service.lookup(key);


    u64 *start =
        request_start.lookup(key);


    if (service && start) {

        if (ret > 0) {

            u64 now =
                bpf_ktime_get_ns();


            u64 delta =
                now - *start;


            inc_latency_count(
                *service
            );


            add_latency(
                *service,
                delta
            );


            request_start.delete(key);

        } else if (ret == 0) {

            /*
             * EOF while request was pending.
             */

            inc_io_error(*service);

            request_start.delete(key);

        } else if (
            ret != -11 &&
            ret != -4
        ) {

            inc_io_error(*service);

            request_start.delete(key);
        }
    }


    read_fd_by_tid.delete(&id);


    return 0;
}


TRACEPOINT_PROBE(syscalls, sys_enter_read)
{
    return remember_read_fd(
        args->fd
    );
}


TRACEPOINT_PROBE(syscalls, sys_exit_read)
{
    return finish_read(
        args->ret
    );
}


TRACEPOINT_PROBE(syscalls, sys_enter_readv)
{
    return remember_read_fd(
        args->fd
    );
}


TRACEPOINT_PROBE(syscalls, sys_exit_readv)
{
    return finish_read(
        args->ret
    );
}

TRACEPOINT_PROBE(syscalls, sys_enter_recvfrom)
{
    return remember_read_fd(args->fd);
}

TRACEPOINT_PROBE(syscalls, sys_exit_recvfrom)
{
    return finish_read(args->ret);
}


TRACEPOINT_PROBE(syscalls, sys_enter_recvmsg)
{
    return remember_read_fd(args->fd);
}

TRACEPOINT_PROBE(syscalls, sys_exit_recvmsg)
{
    return finish_read(args->ret);
}


/* ============================================================
 * CLOSE
 * ============================================================ */

TRACEPOINT_PROBE(syscalls, sys_enter_close)
{
    u32 tgid = current_tgid();

    if (tgid != TARGET_PID)
        return 0;


    struct fd_key key = {};

    key.tgid = tgid;
    key.fd = args->fd;


    fd_service.delete(&key);

    request_start.delete(&key);


    return 0;
}
"""


program = program.replace(
    "TARGET_PID",
    str(TARGET_PID)
)


b = BPF(text=program)


b.attach_kprobe(
    event="tcp_sendmsg",
    fn_name="trace_tcp_sendmsg"
)

b.attach_kretprobe(
    event="tcp_sendmsg",
    fn_name="trace_tcp_sendmsg_ret"
)


b.attach_kprobe(
    event="tcp_recvmsg",
    fn_name="trace_tcp_recvmsg"
)

b.attach_kretprobe(
    event="tcp_recvmsg",
    fn_name="trace_tcp_recvmsg_ret"
)


def preload_existing_sockets(pid):

    inode_to_service = {}


    for path in (
        f"/proc/{pid}/net/tcp",
        f"/proc/{pid}/net/tcp6"
    ):

        try:

            with open(
                path,
                "r",
                encoding="utf-8"
            ) as f:

                next(f, None)

                for line in f:

                    parts = line.split()

                    if len(parts) < 10:
                        continue


                    remote = parts[2]

                    inode = parts[9]


                    try:

                        remote_port = int(
                            remote.split(":")[1],
                            16
                        )

                    except (
                        ValueError,
                        IndexError
                    ):

                        continue


                    service = (
                        PORT_TO_SERVICE.get(
                            remote_port
                        )
                    )


                    if service:

                        inode_to_service[
                            inode
                        ] = service


        except FileNotFoundError:

            pass


    fd_table = b["fd_service"]

    loaded = 0


    for fd_path in glob.glob(
        f"/proc/{pid}/fd/*"
    ):

        try:

            target = os.readlink(
                fd_path
            )

        except OSError:

            continue


        match = re.fullmatch(
            r"socket:\[(\d+)\]",
            target
        )


        if not match:
            continue


        inode = match.group(1)


        service = (
            inode_to_service.get(
                inode
            )
        )


        if not service:
            continue


        try:

            fd = int(
                os.path.basename(
                    fd_path
                )
            )

        except ValueError:

            continue


        key = fd_table.Key()

        key.tgid = pid
        key.fd = fd


        fd_table[key] = (
            fd_table.Leaf(
                service
            )
        )


        loaded += 1


    return loaded


def hash_value(
    table_name,
    key_value
):

    table = b[table_name]

    key = table.Key(
        key_value
    )


    try:

        return int(
            table[key].value
        )

    except KeyError:

        return 0


def array_value(
    table_name,
    index
):

    table = b[table_name]

    key = table.Key(
        index
    )


    try:

        return int(
            table[key].value
        )

    except KeyError:

        return 0


def outstanding_by_service():

    result = {
        service: 0
        for service in SERVICES
    }


    starts = b[
        "request_start"
    ]

    fd_map = b[
        "fd_service"
    ]


    for key, _ in starts.items():

        try:

            service = int(
                fd_map[key].value
            )

        except KeyError:

            continue


        if service in result:

            result[service] += 1


    return result


preloaded = preload_existing_sockets(
    TARGET_PID
)


print(
    f"Monitoring order-service host PID: "
    f"{TARGET_PID}"
)

print(
    f"Preloaded existing outbound sockets: "
    f"{preloaded}"
)

print(
    "Latency = successful write/writev "
    "-> first successful read/readv"
)

print(
    "This is a round-trip proxy, "
    "NOT an HTTP trace."
)

print(
    "Press Ctrl+C to stop.\n"
)


prev_tx = hash_value(
    "sent_bytes",
    TARGET_PID
)

prev_rx = hash_value(
    "recv_bytes",
    TARGET_PID
)


prev = {}


for service in SERVICES:

    prev[service] = {

        "request_count":
            array_value(
                "request_count",
                service
            ),

        "latency_count":
            array_value(
                "latency_count",
                service
            ),

        "latency_total":
            array_value(
                "latency_ns_total",
                service
            ),

        "connect_attempts":
            array_value(
                "connect_attempts",
                service
            ),

        "connect_errors":
            array_value(
                "connect_errors",
                service
            ),

        "io_errors":
            array_value(
                "io_errors",
                service
            ),
    }


try:

    while True:

        time.sleep(1)


        tx = hash_value(
            "sent_bytes",
            TARGET_PID
        )

        rx = hash_value(
            "recv_bytes",
            TARGET_PID
        )


        tx_rate = (
            max(
                0,
                tx - prev_tx
            )
            / 1024.0
        )


        rx_rate = (
            max(
                0,
                rx - prev_rx
            )
            / 1024.0
        )


        prev_tx = tx
        prev_rx = rx


        outstanding = (
            outstanding_by_service()
        )


        print(
            f"TX {tx_rate:8.2f} KiB/s | "
            f"RX {rx_rate:8.2f} KiB/s"
        )


        for service, name in (
            SERVICES.items()
        ):

            req = array_value(
                "request_count",
                service
            )

            lat_count = array_value(
                "latency_count",
                service
            )

            lat_total = array_value(
                "latency_ns_total",
                service
            )

            lat_max = array_value(
                "latency_ns_max",
                service
            )

            conn = array_value(
                "connect_attempts",
                service
            )

            conn_err = array_value(
                "connect_errors",
                service
            )

            io_err = array_value(
                "io_errors",
                service
            )


            d_req = (
                req -
                prev[service][
                    "request_count"
                ]
            )


            d_lat_count = (
                lat_count -
                prev[service][
                    "latency_count"
                ]
            )


            d_lat_total = (
                lat_total -
                prev[service][
                    "latency_total"
                ]
            )


            d_conn = (
                conn -
                prev[service][
                    "connect_attempts"
                ]
            )


            d_conn_err = (
                conn_err -
                prev[service][
                    "connect_errors"
                ]
            )


            d_io_err = (
                io_err -
                prev[service][
                    "io_errors"
                ]
            )


            if d_lat_count > 0:

                avg_ms = (
                    d_lat_total
                    / d_lat_count
                    / 1_000_000.0
                )

                avg_text = (
                    f"{avg_ms:7.2f} ms"
                )

            else:

                avg_text = "      -"


            max_ms = (
                lat_max
                / 1_000_000.0
            )


            print(

                f"  {name:<14} "

                f"req={d_req:4d}/s "

                f"samples="
                f"{d_lat_count:4d}/s "

                f"avg={avg_text} "

                f"max*="
                f"{max_ms:8.2f} ms "

                f"conn={d_conn:3d} "

                f"conn_err="
                f"{d_conn_err:2d} "

                f"io_err="
                f"{d_io_err:2d} "

                f"pending="
                f"{outstanding.get(service, 0):2d}"
            )


            prev[service][
                "request_count"
            ] = req

            prev[service][
                "latency_count"
            ] = lat_count

            prev[service][
                "latency_total"
            ] = lat_total

            prev[service][
                "connect_attempts"
            ] = conn

            prev[service][
                "connect_errors"
            ] = conn_err

            prev[service][
                "io_errors"
            ] = io_err


        print()


except KeyboardInterrupt:

    print("\nStopped.")
