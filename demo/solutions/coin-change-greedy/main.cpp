#include <bits/stdc++.h>
using namespace std;

int main() {
    int n;
    cin >> n;
    int coins = 0;
    for (int c : {25, 10, 5, 1}) {
        coins += n / c;
        n %= c;
    }
    cout << coins << "\n";
}
