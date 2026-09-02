#include <bits/stdc++.h>
using namespace std;

int main() {
    int n;
    long long t;
    cin >> n >> t;
    vector<long long> v(n);
    for (auto &x : v) cin >> x;

    // Seen-set walk: for each value, look for its complement among the ones already read, which
    // is what makes `2 2` a valid pair without letting a single element pair with itself.
    unordered_set<long long> seen;
    for (long long x : v) {
        if (seen.count(t - x)) { cout << "YES\n"; return 0; }
        seen.insert(x);
    }
    cout << "NO\n";
}
