"""Dedicated API-only integration test; localhost ports and an isolated build world."""
import json
import os
from pathlib import Path
import socket
import struct
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
GAME = ROOT / "neoforge/build/breeding-smoke"
EVIDENCE = ROOT / "build/breeding-smoke-evidence"
PASSWORD = "zian-standalone-local-test"

def rcon(command):
    def read_exact(sock, length):
        data = b""
        while len(data) < length:
            block = sock.recv(length - len(data))
            if not block:
                raise EOFError("RCON disconnected")
            data += block
        return data
    def receive(sock):
        size = struct.unpack("<i", read_exact(sock, 4))[0]
        body = read_exact(sock, size)
        return struct.unpack("<ii", body[:8]), body[8:-2].decode("utf-8", errors="replace")
    def send(sock, rid, kind, text):
        data = struct.pack("<ii", rid, kind) + text.encode() + b"\0\0"
        sock.sendall(struct.pack("<i", len(data)) + data)
    with socket.create_connection(("127.0.0.1", 25987), 10) as sock:
        sock.settimeout(20)
        send(sock, 1, 3, PASSWORD)
        if receive(sock)[0][0] == -1:
            raise RuntimeError("Test RCON authentication failed")
        send(sock, 2, 2, command)
        try:
            return receive(sock)[1]
        except EOFError:
            if command != "stop":
                raise
            return "Stopped"

def wait_for(process, log, text, timeout=240, offset=0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        content = log.read_text(encoding="utf-8", errors="replace") if log.exists() else ""
        tail = content[offset:]
        if "Breeding native smoke FAILED" in tail or "Encountered an unexpected exception" in tail:
            raise RuntimeError(tail[-12000:])
        if text in tail:
            return
        if process.poll() is not None:
            raise RuntimeError(f"Server exited waiting for {text}:\n{tail[-12000:]}")
        time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {text}")

def run(pass_number):
    log = EVIDENCE / f"server-{pass_number}.log"
    env = dict(os.environ, ZIANUTILITIES_BREEDING_SMOKE="true")
    gradle = str(ROOT / "gradlew.bat") if os.name == "nt" else "gradle"
    with log.open("w", encoding="utf-8") as output:
        command = [gradle, "--no-daemon", ":neoforge:runBreedingSmoke"]
        if os.environ.get("ZIANUTILITIES_SMOKE_GRADLE_INIT"):
            command += ["--init-script", os.environ["ZIANUTILITIES_SMOKE_GRADLE_INIT"]]
        # A fresh CI checkout needs MDG's generated asset properties. Existing local
        # environments can skip the download after that required file exists.
        if (ROOT / "build/moddev/minecraft_assets.properties").exists():
            command += ["-x", "downloadAssets"]
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        try:
            wait_for(process, log, "Breeding native smoke PASSED:")
            rcon("stop")
            process.wait(timeout=60)
            if process.returncode != 0:
                raise RuntimeError(f"Dedicated server exited with {process.returncode}")
        finally:
            if process.poll() is None:
                try:
                    rcon("stop")
                    process.wait(timeout=30)
                except Exception:
                    process.terminate()
                    process.wait(timeout=15)
    print(f"Standalone server pass {pass_number} completed")

if __name__ == "__main__":
    if os.environ.get("ZIAN_STORAGE_SQLITE_SMOKE")=="true" and GAME.exists():
        import uuid
        expected=(ROOT/"neoforge/build/breeding-smoke").resolve()
        if GAME.resolve()!=expected or not expected.is_relative_to((ROOT/"neoforge/build").resolve()):raise RuntimeError("Unexpected SQL fixture path")
        GAME.rename(ROOT/"neoforge/build"/("breeding-smoke-previous-"+str(uuid.uuid4())))
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (GAME / "config").mkdir(parents=True, exist_ok=True)
    (GAME / "config/zianutilities-breeding.json").write_text(json.dumps({"enabled": True, "baseMinutes": 1440, "minimumMinutes": 120, "defaultSlots": 1, "vipSlots": 2, "maxSlots": 8, "alphaChance": 0.05, "currencyReductionsMinutes": {}}), encoding="utf-8")
    (GAME / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (GAME / "server.properties").write_text(f"server-ip=127.0.0.1\nserver-port=25986\nonline-mode=false\nenable-rcon=true\nrcon.password={PASSWORD}\nrcon.port=25987\nmax-players=4\nview-distance=2\nsimulation-distance=2\n", encoding="utf-8")
    if os.environ.get("ZIANUTILITIES_SMOKE_AVECOINS_JAR"):
        (GAME / "mods").mkdir(exist_ok=True)
        shutil.copyfile(os.environ["ZIANUTILITIES_SMOKE_AVECOINS_JAR"],GAME / "mods/avecoins-2.4.jar")
    if os.environ.get("ZIAN_STORAGE_SQLITE_SMOKE") == "true":
        world=GAME/"world"
        world.mkdir(parents=True,exist_ok=True)
        java=Path(os.environ["JAVA_HOME"])/"bin"/("java.exe" if os.name=="nt" else "java")
        classpath=os.pathsep.join([str(ROOT/"core/build/classes/java/main"),str(ROOT/"core/build/resources/main")])
        subprocess.run([str(java),"-cp",classpath,"com.zianblk.zianutilities.core.storage.StorageMaintenance","migrate-offline",str(world)],check=True)
    run(1)
    run(2)
