#include <bits/stdc++.h>
using namespace std;

int main() {
    int n;
    cin >> n;
    vector<long long> v(n);
    for (auto &x : v) cin >> x;
    sort(v.begin(), v.end());
    for (int i = 0; i < n; i++) cout << v[i] << (i + 1 < n ? ' ' : '\n');
}
