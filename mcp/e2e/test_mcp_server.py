#!/usr/bin/env python3
import http.client
import os
import signal
import subprocess
import sys
import time

MCP_DIR = os.path.dirname(os.path.dirname(__file__))  # .../mcp
PROJECT_ROOT = os.path.abspath(os.path.join(MCP_DIR, os.pardir))  # repo root with ./gradlew
PORT = int(os.environ.get("MCP_PORT", "3001"))


def wait_for_http_ok(path: str, timeout: float = 20.0) -> str:
    deadline = time.time() + timeout
    last_err = None
    while time.time() < deadline:
        try:
            conn = http.client.HTTPConnection("127.0.0.1", PORT, timeout=2.0)
            conn.request("GET", path)
            resp = conn.getresponse()
            body = resp.read().decode("utf-8", errors="ignore")
            if resp.status == 200:
                return body
        except Exception as e:
            last_err = e
        finally:
            try:
                conn.close()
            except Exception:
                pass
        time.sleep(0.25)
    raise RuntimeError(f"Server did not respond 200 on {path}: {last_err}")


def check_sse_endpoint() -> None:
    conn = http.client.HTTPConnection("127.0.0.1", PORT, timeout=3.0)
    try:
        conn.putrequest("GET", "/sse")
        conn.putheader("Accept", "text/event-stream")
        conn.endheaders()
        resp = conn.getresponse()
        ctype = resp.getheader("Content-Type") or resp.getheader("content-type")
        assert resp.status == 200, f"/sse status {resp.status}"
        assert ctype and "text/event-stream" in ctype, f"Unexpected Content-Type: {ctype}"
    finally:
        try:
            conn.close()
        except Exception:
            pass


def main() -> int:
    gradlew = os.path.join(PROJECT_ROOT, "gradlew")
    if not os.path.exists(gradlew):
        raise SystemExit("gradlew not found at repo root")

    env = os.environ.copy()
    cmd = [gradlew, "--no-daemon", ":mcp:run"]
    print("Starting mcp server...", flush=True)
    proc = subprocess.Popen(cmd, cwd=PROJECT_ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        body = wait_for_http_ok("/")
        assert "MCP server running" in body, body
        print("Health endpoint OK")

        check_sse_endpoint()
        print("SSE endpoint OK")
        return 0
    finally:
        try:
            if proc.poll() is None:
                proc.send_signal(signal.SIGINT)
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
        except Exception:
            pass
        if proc.stdout is not None:
            out = proc.stdout.read().decode("utf-8", errors="ignore")
            if out:
                sys.stdout.write("\n--- server output ---\n" + out + "\n----------------------\n")


if __name__ == "__main__":
    sys.exit(main())
