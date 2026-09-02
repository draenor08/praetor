#include <bits/stdc++.h>
using namespace std;
using ll = long long;

int main() {
    ll n;
    cin >> n;
    // n*(n+1) reaches ~1e18: fine in long long, overflow in int.
    cout << n * (n + 1) / 2 << "\n";
}
