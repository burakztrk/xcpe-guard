import sys

BAD_IPS = {"219.134.163.210", "219.134.163.211", "23.94.49.57", "38.145.197.135"}
MQTT_PORTS = range(3885, 3888)


def decode(hex_ip):
    if len(hex_ip) == 32:
        if not hex_ip.startswith("0000000000000000FFFF0000"):
            return None
        hex_ip = hex_ip[24:]
    if len(hex_ip) != 8:
        return None
    v = int(hex_ip, 16)
    return f"{v & 255}.{(v >> 8) & 255}.{(v >> 16) & 255}.{(v >> 24) & 255}"


hits = []
total = 0
for line in sys.stdin:
    f = line.split()
    if len(f) < 8 or f[0] == "sl" or ":" not in f[2] or f[3] == "0A":
        continue
    ip = decode(f[2].split(":")[0])
    if ip is None or ip == "0.0.0.0":
        continue
    port = int(f[2].split(":")[1], 16)
    total += 1
    if ip in BAD_IPS or port in MQTT_PORTS:
        hits.append(f"{ip}:{port} uid={f[7]}")

print(f"{total} active TCP connections checked")
if hits:
    print("C2 CONNECTIONS FOUND:")
    for h in hits:
        print(f"  {h}")
    sys.exit(1)
print("no connection to a C2 address or the MQTT ports")
