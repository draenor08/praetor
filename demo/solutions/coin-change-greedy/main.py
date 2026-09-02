n = int(input())
coins = 0
for c in (25, 10, 5, 1):
    coins += n // c
    n %= c
print(coins)
