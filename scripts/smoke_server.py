"""Start a dedicated NeoForge server twice and stop it through local RCON.

The isolated test world lives under build/. No development or player world is touched.
"""
from pathlib import Path
import os
import socket
import struct
import subprocess
import threading
import time


ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "build" / "server-smoke"
RUN.mkdir(parents=True, exist_ok=True)
with socket.socket() as probe:
    probe.bind(("127.0.0.1", 0))
    rcon_port = probe.getsockname()[1]
(RUN / "eula.txt").write_text("eula=true\n")
(RUN / "server.properties").write_text(
    "level-name=smoke-world\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
    "view-distance=2\nsimulation-distance=2\nspawn-protection=0\n"
    "enable-rcon=true\nrcon.password=wwmc-local-smoke\n"
    f"rcon.port={rcon_port}\n"
)


def read_exact(connection, size):
    result = bytearray()
    while len(result) < size:
        chunk = connection.recv(size - len(result))
        if not chunk:
            raise ConnectionError("RCON closed before returning a full packet")
        result.extend(chunk)
    return bytes(result)


def exchange(connection, request, kind, text):
    packet = struct.pack("<ii", request, kind) + text.encode() + b"\0\0"
    connection.sendall(struct.pack("<i", len(packet)) + packet)
    for _ in range(8):
        length = struct.unpack("<i", read_exact(connection, 4))[0]
        if not 10 <= length <= 65536:
            raise ValueError(f"Invalid RCON packet length: {length}")
        payload = read_exact(connection, length)
        response, response_kind = struct.unpack("<ii", payload[:8])
        if response == -1:
            raise PermissionError("Local smoke-test RCON authentication failed")
        if response == request and (kind != 3 or response_kind == 2):
            return payload[8:-2].decode(errors="replace")
    raise ConnectionError("RCON did not answer the smoke-test request")


def start_and_stop(attempt):
    ready = threading.Event()
    log_path = RUN / f"startup-{attempt}.log"
    process = subprocess.Popen(
        [str(ROOT / "gradlew"), "runServerSmoke", "--console=plain", "--no-configuration-cache"],
        cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
        start_new_session=True,
    )

    def record():
        with log_path.open("w") as log:
            for line in process.stdout:
                log.write(line)
                log.flush()
                if "Done (" in line and 'For help, type "help"' in line:
                    ready.set()

    reader = threading.Thread(target=record, daemon=True)
    reader.start()
    deadline = time.monotonic() + 240
    try:
        while not ready.wait(0.25):
            if process.poll() is not None or time.monotonic() >= deadline:
                raise RuntimeError(f"Dedicated server did not become ready; see {log_path}")
        with socket.create_connection(("127.0.0.1", rcon_port), timeout=20) as connection:
            exchange(connection, 10, 3, "wwmc-local-smoke")
            saved = exchange(connection, 11, 2, "save-all flush")
            if "Saved" not in saved:
                raise RuntimeError(f"Server did not confirm a world save: {saved}")
            exchange(connection, 12, 2, "stop")
        process.wait(timeout=90)
        reader.join(timeout=10)
        if process.returncode:
            raise RuntimeError(f"Dedicated server exited with {process.returncode}; see {log_path}")
        print(f"Dedicated server startup {attempt}: ready, saved, stopped cleanly", flush=True)
    except BaseException:
        if process.poll() is None:
            os.killpg(process.pid, 15)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, 9)
                process.wait(timeout=10)
        reader.join(timeout=10)
        if log_path.exists():
            print(log_path.read_text()[-18000:], flush=True)
        raise


start_and_stop(1)
metadata = RUN / "smoke-world" / "data" / "minecraft" / "world_gen_settings.dat"
if not metadata.is_file():
    raise RuntimeError(f"Dedicated server failed to save world generation metadata: {metadata}")
start_and_stop(2)
print("Dedicated-server fresh-world and saved-world smoke tests passed", flush=True)
