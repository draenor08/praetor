import sys

data = sys.stdin.read().split()
n, t = int(data[0]), int(data[1])
v = list(map(int, data[2 : 2 + n]))

seen = set()
for x in v:
    if t - x in seen:
        print("YES")
        break
    seen.add(x)
else:
    print("NO")
