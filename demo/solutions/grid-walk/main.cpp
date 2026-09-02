#include <bits/stdc++.h>
using namespace std;
using ll = long long;

const ll MOD = 1e9 + 7;

int main() {
    int n, m;
    cin >> n >> m;

    // dp[j] = paths to the current row's column j. Rolling one row keeps this at O(m) memory.
    vector<ll> dp(m, 0);
    dp[0] = 1;
    for (int i = 0; i < n; i++) {
        for (int j = 1; j < m; j++) {
            dp[j] = (dp[j] + dp[j - 1]) % MOD;
        }
    }
    cout << dp[m - 1] << "\n";
}
