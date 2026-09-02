#include <bits/stdc++.h>
using namespace std;
using ll = long long;

int main() {
    ll n;
    cin >> n;
    bool prime = n >= 2;
    for (ll d = 2; d * d <= n; d++) {
        if (n % d == 0) { prime = false; break; }
    }
    cout << (prime ? "YES" : "NO") << "\n";
}
