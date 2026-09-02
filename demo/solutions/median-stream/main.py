import heapq
import sys

data = sys.stdin.read().split()
n = int(data[0])

low = []   # max-heap via negation, the smaller half
high = []  # min-heap, the larger half
out = []
for i in range(1, n + 1):
    x = int(data[i])

    if not low or x <= -low[0]:
        heapq.heappush(low, -x)
    else:
        heapq.heappush(high, x)

    if len(low) > len(high) + 1:
        heapq.heappush(high, -heapq.heappop(low))
    elif len(high) > len(low):
        heapq.heappush(low, -heapq.heappop(high))

    out.append(-low[0])

print("\n".join(map(str, out)))
