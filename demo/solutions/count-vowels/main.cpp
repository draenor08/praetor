#include <bits/stdc++.h>
using namespace std;

int main() {
    string s;
    cin >> s;
    int count = 0;
    for (char c : s) {
        if (string("aeiou").find(c) != string::npos) count++;
    }
    cout << count << "\n";
}
