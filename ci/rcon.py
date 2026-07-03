#!/usr/bin/env python3
"""Minimal RCON client for the CI integration test."""
import socket
import struct
import sys


class RconClient:
    def __init__(self, host, port, password, timeout=15):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self._send(1, 3, password)
        req_id, _, _ = self._read()
        if req_id == -1:
            raise RuntimeError("RCON auth failed")

    def _send(self, req_id, ptype, payload):
        data = struct.pack("<ii", req_id, ptype) + payload.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _read(self):
        raw = self.sock.recv(4)
        if len(raw) < 4:
            return None, None, ""
        (length,) = struct.unpack("<i", raw)
        data = b""
        while len(data) < length:
            chunk = self.sock.recv(length - len(data))
            if not chunk:
                break
            data += chunk
        req_id, ptype = struct.unpack("<ii", data[:8])
        return req_id, ptype, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        self._send(2, 2, command)
        _, _, body = self._read()
        return body

    def close(self):
        self.sock.close()


def main():
    client = RconClient("127.0.0.1", 25575, "test123")
    for command in sys.argv[1:]:
        print(f"> {command}")
        print(client.cmd(command))
    client.close()


if __name__ == "__main__":
    main()
