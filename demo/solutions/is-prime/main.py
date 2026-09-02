n = int(input())
prime = n >= 2
d = 2
while d * d <= n:
    if n % d == 0:
        prime = False
        break
    d += 1
print("YES" if prime else "NO")
