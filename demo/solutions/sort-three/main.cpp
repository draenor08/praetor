#include <bits/stdc++.h>
using namespace std;

int main() {
    vector<long long> v(3);
    for (auto &x : v) cin >> x;
    sort(v.begin(), v.end());
    cout << v[0] << " " << v[1] << " " << v[2] << "\n";
}
