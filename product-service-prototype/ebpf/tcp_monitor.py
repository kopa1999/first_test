#!/usr/bin/env python3

from bcc import BPF
import argparse
import time

parser = argparse.ArgumentParser()
parser.add_argument("--pid", type=int, required=True)
args = parser.parse_args()

TARGET_PID = args.pid

program = r"""
#include <uapi/linux/ptrace.h>

BPF_HASH(active_send, u64, u8);
BPF_HASH(active_recv, u64, u8);

BPF_HASH(sent_bytes, u32, u64);
BPF_HASH(recv_bytes, u32, u64);

int trace_tcp_sendmsg_entry(struct pt_regs *ctx)
{
    u64 id = bpf_get_current_pid_tgid();
    u32 tgid = id >> 32;

    if (tgid != TARGET_PID)
        return 0;

    u8 one = 1;
    active_send.update(&id, &one);

    return 0;
}

int trace_tcp_sendmsg_return(struct pt_regs *ctx)
{
    u64 id = bpf_get_current_pid_tgid();

    u8 *active = active_send.lookup(&id);
    if (!active)
        return 0;

    active_send.delete(&id);

    int ret = (int)PT_REGS_RC(ctx);

    if (ret <= 0)
        return 0;

    u32 tgid = id >> 32;

    u64 zero = 0;
    u64 *value = sent_bytes.lookup_or_init(&tgid, &zero);

    if (value)
        __sync_fetch_and_add(value, ret);

    return 0;
}

int trace_tcp_recvmsg_entry(struct pt_regs *ctx)
{
    u64 id = bpf_get_current_pid_tgid();
    u32 tgid = id >> 32;

    if (tgid != TARGET_PID)
        return 0;

    u8 one = 1;
    active_recv.update(&id, &one);

    return 0;
}

int trace_tcp_recvmsg_return(struct pt_regs *ctx)
{
    u64 id = bpf_get_current_pid_tgid();

    u8 *active = active_recv.lookup(&id);
    if (!active)
        return 0;

    active_recv.delete(&id);

    int ret = (int)PT_REGS_RC(ctx);

    if (ret <= 0)
        return 0;

    u32 tgid = id >> 32;

    u64 zero = 0;
    u64 *value = recv_bytes.lookup_or_init(&tgid, &zero);

    if (value)
        __sync_fetch_and_add(value, ret);

    return 0;
}
"""

program = program.replace("TARGET_PID", str(TARGET_PID))

b = BPF(text=program)

b.attach_kprobe(
    event="tcp_sendmsg",
    fn_name="trace_tcp_sendmsg_entry"
)

b.attach_kretprobe(
    event="tcp_sendmsg",
    fn_name="trace_tcp_sendmsg_return"
)

b.attach_kprobe(
    event="tcp_recvmsg",
    fn_name="trace_tcp_recvmsg_entry"
)

b.attach_kretprobe(
    event="tcp_recvmsg",
    fn_name="trace_tcp_recvmsg_return"
)

print(f"Monitoring order-service PID {TARGET_PID}")
print("Press Ctrl+C to stop\n")

last_sent = 0
last_recv = 0

try:
    while True:
        time.sleep(1)

        sent = sum(v.value for _, v in b["sent_bytes"].items())
        recv = sum(v.value for _, v in b["recv_bytes"].items())

        sent_delta = sent - last_sent
        recv_delta = recv - last_recv

        print(
            f"TX: {sent_delta / 1024:.2f} KiB/s | "
            f"RX: {recv_delta / 1024:.2f} KiB/s"
        )

        last_sent = sent
        last_recv = recv

except KeyboardInterrupt:
    print("\nStopped.")
