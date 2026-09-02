import sys

data = sys.stdin.read().split()
n = int(data[0])
v = sorted(map(int, data[1 : 1 + n]))
print(*v)
