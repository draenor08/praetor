#include <bits/stdc++.h>
using namespace std;
using ll = long long;

int main() {
    ll a, b;
    cin >> a >> b;
    // 1e9 + 1e9 overflows int32 — test 4 is exactly that case.
    cout << a + b << "\n";
}
