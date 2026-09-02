MOD = 10**9 + 7

n, m = map(int, input().split())

dp = [0] * m
dp[0] = 1
for _ in range(n):
    for j in range(1, m):
        dp[j] = (dp[j] + dp[j - 1]) % MOD
print(dp[m - 1])
