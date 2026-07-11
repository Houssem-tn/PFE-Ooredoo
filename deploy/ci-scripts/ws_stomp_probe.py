#!/usr/bin/env python3
"""STOMP-over-WebSocket CONNECT probe, used by the integration workflow.

Plain curl can perform the raw HTTP -> WebSocket upgrade (see the
"WebSocket upgrade handshake" step in integration.yml) but cannot speak
STOMP framing on top of it, so it can't observe whether a CONNECT is
actually accepted or rejected by WebSocketAuthChannelInterceptor
(src/main/java/com/pfe/Security/WebSocketAuthChannelInterceptor.java).
That interceptor only rejects during CONNECT processing (a STOMP ERROR
frame + socket close), never at the HTTP upgrade itself — so "101
received" is not proof of anything auth-related. This script sends a
real CONNECT frame (optionally with the exact native STOMP header the
interceptor reads: "Authorization: Bearer <jwt>") and reports whether the
server replied CONNECTED or ERROR.
"""
import argparse
import asyncio
import sys

import websockets


async def probe(url: str, token: str | None, timeout: float) -> str:
    async with websockets.connect(url, open_timeout=timeout, close_timeout=timeout) as ws:
        headers = "accept-version:1.2,1.1,1.0\nhost:localhost\n"
        if token:
            headers += f"Authorization:Bearer {token}\n"
        frame = f"CONNECT\n{headers}\n\x00"
        await ws.send(frame)
        try:
            response = await asyncio.wait_for(ws.recv(), timeout=timeout)
        except asyncio.TimeoutError:
            return "TIMEOUT"
        except websockets.exceptions.ConnectionClosed:
            return "CLOSED_NO_FRAME"
    if response.startswith("CONNECTED"):
        return "CONNECTED"
    if response.startswith("ERROR"):
        return "ERROR"
    return f"UNKNOWN:{response[:120]!r}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="ws:// or wss:// STOMP endpoint URL")
    parser.add_argument("--token", default=None, help="JWT to send as a STOMP Authorization header (omit to test the missing-auth case)")
    parser.add_argument("--expect", required=True, choices=["CONNECTED", "ERROR"])
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    outcome = asyncio.run(probe(args.url, args.token, args.timeout))
    print(f"STOMP CONNECT outcome: {outcome}")
    if outcome != args.expect:
        print(f"::error::Expected {args.expect}, got {outcome}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
