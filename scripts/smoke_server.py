"""Start a dedicated NeoForge server twice and stop it through local RCON.

The isolated test world lives under build/. No development or player world is touched.
"""
from pathlib import Path
import os
import json
import re
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
    "level-name=smoke-world\nlevel-seed=246813579\ngenerate-structures=true\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
    "view-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=0\n"
    # /forceload generates its chunks within one server tick, which play never does; allow it five minutes.
    "max-tick-time=300000\n"
    "enable-rcon=true\nrcon.password=vc-local-smoke\n"
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


COORDINATES = re.compile(r"\[(-?\d+),\s*(-?\d+|~),\s*(-?\d+)\]")


def locate(connection, request, target, label):
    # A structure search runs on the server thread and may pass many regions before it finds a rare one.
    connection.settimeout(120)
    started = time.monotonic()
    try:
        located = exchange(connection, request, 2, f"locate structure {target}")
    finally:
        connection.settimeout(20)
    match = COORDINATES.search(located)
    if not match:
        raise RuntimeError(f"Normal-world {label} locate failed: {located}")
    print(f"Located {target} in {time.monotonic() - started:.1f} s", flush=True)
    return int(match[1]), int(match[3])


def settlement_boxes(x, z, extent):
    """The area a settlement centred here builds on, as one forceload box."""
    return [(x - extent, z - extent, x + extent, z + extent)]


def city_boxes(x, z, reach_chunks=7):
    """A city stands up to five chunks from the chunk /locate names, so its land lies within seven chunks of it.

    Forcing all of that in one command generates it in one server tick, long enough for the watchdog to stop the
    server, so it is forced five by five chunks at a time, nearest first.
    """
    cx, cz = x // 16, z // 16
    step = (2 * reach_chunks + 1) // 3
    offsets = sorted(((dx, dz) for dx in (-step, 0, step) for dz in (-step, 0, step)), key=lambda d: abs(d[0]) + abs(d[1]))
    half = step // 2
    return [((cx + dx - half) * 16, (cz + dz - half) * 16, (cx + dx + half) * 16 + 15, (cz + dz + half) * 16 + 15)
            for dx, dz in offsets]


def verify_settlement(connection, request, x, z, boxes, crew_range, minimum, seconds, label, shift=0):
    """Force a located settlement's land and wait for its saved claim, banner and crew.

    /locate names the chunk that starts the structure; a city may stand up to `shift` blocks from it.
    """
    deadline = time.monotonic() + seconds
    pending = list(boxes)
    banner = crew = town = "not inspected"
    while True:
        if pending:
            x1, z1, x2, z2 = pending.pop(0)
            # The server generates forced chunks before it replies.
            connection.settimeout(120)
            try:
                exchange(connection, request, 2, f"forceload add {x1} {z1} {x2} {z2}")
            finally:
                connection.settimeout(20)
        town = exchange(connection, request + 1, 2, f"execute positioned {x} 80 {z} run locate settlement")
        if "The nearest AI settlement" in town:
            match = re.search(r"is at \[?(-?\d+),\s*(-?\d+),\s*(-?\d+)\]?", town)
            if match and abs(int(match[1]) - x) <= shift and abs(int(match[3]) - z) <= shift:
                sx, y, sz = int(match[1]), int(match[2]), int(match[3])
                banner = exchange(connection, request + 2, 2, f"execute if block {sx} {y} {sz} vc:settlement_banner")
                crew = exchange(connection, request + 3, 2,
                                f"execute positioned {sx} {y} {sz} if entity @e[type=vc:citizen,distance=..{crew_range}]")
                count = re.search(r"count: (\d+)", crew, re.IGNORECASE)
                if "Test passed" in banner and count and int(count[1]) >= minimum:
                    return sx, sz
        if time.monotonic() > deadline:
            raise RuntimeError(f"Native {label} verification timed out: town={town}; banner={banner}; crew={crew}")
        if not pending:
            time.sleep(0.25)


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
            exchange(connection, 10, 3, "vc-local-smoke")
            if attempt==1:
                x,z=locate(connection, 20, "vc:settlement", "native town")
                (RUN / "native-town.json").write_text(json.dumps([x,z]))
                print(f"Normal noise world: located vc:settlement at {x}, {z} before loading its village",flush=True)
                cx,cz=locate(connection, 30, "vc:settlement_city", "city")
                if (cx,cz)==(x,z):
                    raise RuntimeError(f"The city and the town were located at the same site: {x}, {z}")
                (RUN / "native-city.json").write_text(json.dumps([cx,cz]))
                print(f"Normal noise world: located vc:settlement_city at {cx}, {cz} before loading it",flush=True)
                locate(connection, 31, "#vc:settlements", "settlement tag")
            else:
                x,z=json.loads((RUN / "native-town.json").read_text())
                cx,cz=json.loads((RUN / "native-city.json").read_text())
            verify_settlement(connection, 21, x, z, settlement_boxes(x, z, 29), 64, 1, 100, "town")
            print(f"Native town startup {attempt}: physical village, saved settlement and real crew verified",flush=True)
            # A city stands up to five chunks from the chunk /locate names.
            sx,sz=verify_settlement(connection, 32, cx, cz, city_boxes(cx, cz), 96, 40, 300, "city", shift=80)
            print(f"Native city startup {attempt}: physical city at {sx}, {sz}, saved settlement and forty citizens verified",flush=True)
            exchange(connection,25,2,"forceload remove all")
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
            text = log_path.read_text()
            # A crash report can fill the tail; the mod's own lines and the server's complaints say what happened.
            keys = ("[VC]", "LocateCommand", "Can't keep up", "Rcon:", "/ERROR]", "/FATAL]", "Description:")
            print("\n".join(line for line in text.splitlines() if any(k in line for k in keys))[-12000:], flush=True)
            print(text[-12000:], flush=True)
        raise


start_and_stop(1)
metadata = RUN / "smoke-world" / "data" / "minecraft" / "world_gen_settings.dat"
if not metadata.is_file():
    raise RuntimeError(f"Dedicated server failed to save world generation metadata: {metadata}")
start_and_stop(2)
print("Dedicated-server fresh-world and saved-world smoke tests passed", flush=True)
